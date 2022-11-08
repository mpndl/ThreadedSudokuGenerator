import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SudokuGenerator {
    // Sudoku Grid Count
    private static final int SGCOUNT = 50000;
    // Threads Per Job (generate + check + save)
    private final int TPJ = 1;
    private final Queue<SudokuGrid> nonUniqueSudokuGrids = new ConcurrentLinkedQueue<>();
    private final Queue<SudokuGrid> checkSudokuGrids = new ConcurrentLinkedQueue<>();
    private final List<SudokuGrid> saveSudokuGrids = Collections.synchronizedList(new ArrayList<>());

    private final Job job = new Job();
    public SudokuGenerator() {
        generate();
    }

    class Job {
        private final Lock genLock = new ReentrantLock();
        private final Lock checkLock = new ReentrantLock();
        private final Lock saveLock = new ReentrantLock();

        private final Condition checkCondition = checkLock.newCondition();
        private final Condition saveCondition = saveLock.newCondition();

        private static int genCount = 1;
        private int saveCount = 0;

        public void generate() {
            while (genCount <= SGCOUNT || nonUniqueSudokuGrids.size() > 0) {
                genLock.lock();
                int order = genCount;
                if (genCount > SGCOUNT) {
                    try {
                        order = Objects.requireNonNull(nonUniqueSudokuGrids.poll()).order();
                    }
                    catch (Exception e) {
                        order = -1;
                    }
                }
                if (order != -1) {
                    SudokuGrid sudokuGrid = new SudokuGrid(order);
                    genCount++;
                    genLock.unlock();
                    reduce(sudokuGrid);
                    if (sudokuGrid.collapsed()) {
                        try {
                            checkLock.lock();
                            checkSudokuGrids.add(sudokuGrid);
                            checkCondition.signalAll();
                        }
                        finally {
                            checkLock.unlock();
                        }
                    } else nonUniqueSudokuGrids.add(sudokuGrid);
                }
            }
        }

        public void check() {
            while (saveCount < SGCOUNT) {
                checkLock.lock();
                while (checkSudokuGrids.size() == 0 && saveCount < SGCOUNT) {
                    try {
                        checkCondition.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                SudokuGrid sudokuGrid = checkSudokuGrids.poll();
                checkLock.unlock();
                try {
                    if (!equal(sudokuGrid)) {
                        try {
                            saveLock.lock();
                            saveSudokuGrids.add(sudokuGrid);
                            saveCondition.signalAll();
                        }
                        finally {
                            saveLock.unlock();
                        }
                    } else {
                        nonUniqueSudokuGrids.add(sudokuGrid);
                    }
                }
                catch (Exception e) {
                    try {
                        checkLock.lock();
                        checkSudokuGrids.add(sudokuGrid);
                        checkCondition.signalAll();
                    }
                    finally {
                        checkLock.unlock();
                    }
                }
            }
        }

        public void save() {
            while (saveCount < SGCOUNT) {
                saveLock.lock();
                while (saveCount < SGCOUNT && saveSudokuGrids.size() == 0) {
                    try {
                        saveCondition.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                SudokuGrid sudokuGrid = saveSudokuGrids.remove(saveSudokuGrids.size() - 1);
                saveCount++;
                saveLock.unlock();
                try {
                    saveToFile(sudokuGrid);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (!(saveCount < SGCOUNT && saveSudokuGrids.size() == 0)) {
                    try {
                        checkLock.lock();

                        checkCondition.signalAll();
                    }
                    finally {
                        checkLock.unlock();
                    }
                }
            }
        }
    }

    // stores sudoku grids in a directors named "50000"
    private void saveToFile(SudokuGrid sudokuGrid) throws IOException {
        new File("50000").mkdir();
        FileWriter writer = new FileWriter("50000/" + sudokuGrid.order() + ".txt");
        writer.write(sudokuGrid.toString());
        writer.close();
    }

    private void generate() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Press [Enter] to generate board | ");
        System.out.print("Type any character to quit");
        String in = scanner.nextLine();
        if(!in.isEmpty())
            System.exit(0);
        System.out.println("Please wait...");
        System.out.println();

        start();
    }

    private void start() {
        for (int i = 0; i < TPJ; i++) {
            new Thread(job::generate).start();
            new Thread(job::check).start();
            new Thread(job::save).start();
        }
    }

    public static void reduce(SudokuGrid sudokuGrid) {
        // pick a box, b, with the least number of possibilities remaining,
        // randomly pick one of the possibilities, p, in that box
        int[] b = sudokuGrid.getLPBox();
        while (b != null) {
            int size = sudokuGrid.retrieve(b[0], b[1]).size();
            b = sudokuGrid.getLPBox();
            if (b != null) {
                int x = ThreadLocalRandom.current().nextInt(size);
                List<Integer> box = sudokuGrid.retrieve(b[0], b[1]);
                int value = box.get(x);
                if (sudokuGrid.next(sudokuGrid.valid(b[0], b[1], value))) {
                    sudokuGrid.set(b[0], b[1], value);
                }
            }
        }
    }

    private boolean equal(SudokuGrid sudokuGrid) {
        if (saveSudokuGrids.size() == 0) return false;
        if (sudokuGrid == null) return false;
        for (int i = 0; i < saveSudokuGrids.size(); i++) {
            SudokuGrid temp = saveSudokuGrids.get(i);
            if (temp != null) {
                if (!rotatedEqual_0(sudokuGrid, temp) && !rotatedEqual_90(sudokuGrid, temp) && !rotatedEqual_180(sudokuGrid, temp)
                        && !rotatedEqual_270(sudokuGrid, temp) && !rowsFlippedEqual(sudokuGrid, temp) && columnsFlippedEqual(sudokuGrid, temp))
                    return false;
            }
        }
        return false;
    }

    private boolean rotatedEqual_0(SudokuGrid b1, SudokuGrid b2) {
        for(int i = 0; i < b1.size(); i++) {
            List<Integer>[] b1Row = b1.row(i);
            List<Integer>[] b2Row = b2.row(i);
            for(int j = 0; j < b1.size(); j++) {
                int b1Value = b1Row[j].get(0);
                int b2Value = b2Row[j].get(0);
                if(b1Value != b2Value)
                    return false;
            }
        }
        return true;
    }

    private boolean rotatedEqual_90(SudokuGrid b1, SudokuGrid b2) {
        for(int i = 0; i < b1.size(); i++) {
            List<Integer>[] b1Row = b1.row(i);
            List<Integer>[] b2Column = b2.column(i);
            for(int j = 0; j < b1.size(); j++) {
                int jReversed = b1.size() - j - 1;
                int b1Value = b1Row[j].get(0);
                int b2Value = b2Column[jReversed].get(0);
                if(b1Value != b2Value)
                    return false;
            }
        }
        return true;
    }

    private boolean rotatedEqual_180(SudokuGrid b1, SudokuGrid b2) {
        for(int i = 0; i < b1.size(); i++) {
            int iReversed = b1.size() - i - 1;
            List<Integer>[] b1Row = b1.row(i);
            List<Integer>[] b2Row = b2.row(iReversed);
            for(int j = 0; j < b1.size(); j++) {
                int jReversed = b1.size() - j - 1;
                int b1Value = b1Row[j].get(0);
                int b2Value = b2Row[jReversed].get(0);
                if(b1Value!= b2Value)
                    return false;
            }
        }
        return true;
    }

    private boolean rotatedEqual_270(SudokuGrid b1, SudokuGrid b2) {
        for(int i = 0; i < b1.size(); i++) {
            int iReversed = b1.size() - i - 1;
            List<Integer>[] b1Row = b1.row(i);
            List<Integer>[] b2Column = b2.column(iReversed);
            for(int j = 0; j < b1.size(); j++) {
                int b1Value = b1Row[j].get(0);
                int b2Value = b2Column[j].get(0);
                if(b1Value != b2Value)
                    return false;
            }
        }
        return true;
    }

    private boolean rowsFlippedEqual(SudokuGrid b1, SudokuGrid b2) {
        for(int i = 0; i < b1.size(); i++) {
            int iReversed = b1.size() - i - 1;
            List<Integer>[] b1Row = b1.row(i);
            List<Integer>[] b2Row = b2.row(iReversed);
            for(int j = 0; j < b1.size(); j++) {
                int b1Value = b1Row[j].get(0);
                int b2Value = b2Row[j].get(0);
                if(b1Value != b2Value)
                    return false;
            }
        }
        return true;
    }

    private boolean columnsFlippedEqual(SudokuGrid b1, SudokuGrid b2) {
        for(int i = 0; i < b1.size(); i++) {
            List<Integer>[] b1Row = b1.row(i);
            List<Integer>[] b2Row = b2.row(i);
            for(int j = 0; j < b1.size(); j++) {
                int jReversed = b1.size() - j - 1;
                int b1Value = b1Row[j].get(0);
                int b2Value = b2Row[jReversed].get(0);
                if(b1Value != b2Value)
                    return false;
            }
        }
        return true;
    }
}