import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class SudokuGrid {
    private int order;
    public List[][] board;
    public enum Validity {
        ROW_VALID, COLUMN_VALID, BLOCK_VALID, ROW_INVALID, COLUMN_INVALID, BLOCK_INVALID;
    }
    public SudokuGrid(int order) {
        board = new List[9][9];
        this.order = order;
        initialize();
    }

    public int order() {
        return order;
    }
    public void setOrder(int order) {this.order = order;}

    public List<int[]> leastPossibilities() {
        List<int[]> lpRowCol = new ArrayList<>();
        int[] b = new int[] {0, 0, Integer.MAX_VALUE};
        for (int i = 0; i < board.length; i++) {
            for (int j = 0; j < board.length; j++) {
                if (!invalidated(new int[] {i, j})) {
                    if (board[i][j].size() > 1 && board[i][j].size() < b[2]) {
                        b[0] = i;
                        b[1] = j;
                        b[2] = board[i][j].size();
                        lpRowCol = new ArrayList<>();
                    }

                    if (board[i][j].size() == b[2])
                        lpRowCol.add(new int[]{i, j});
                }
            }
        }
        return lpRowCol;
    }

    public boolean invalidated(int[] b) {
        List boxValues = board[b[0]][b[1]];
        for (int i = 0; i < boxValues.size(); i++) {
            if (next(valid(b[0], b[1], (Integer) boxValues.get(i)))) {
                return false;
            }
        }
        return true;
    }

    public void clear(int row, int col) {
        board[row][col] = new ArrayList();
    }

    public int[] getLPBox() {
        List<int[]> lpc = leastPossibilities();
        if (lpc.size() > 0) {
            int x = ThreadLocalRandom.current().nextInt(lpc.size());
            return lpc.get(x);
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        ret.append("\t\t\t\t").append("Order: ").append(order).append("\n");
        ret.append("_____________________________________________").append("\n");
        for (int i = 0; i < board.length; i++) {
            for (int j = 0; j < board.length; j++) {
                ArrayList b = (ArrayList) board[i][j];
                String s = b.stream().reduce("", (acc, value) -> acc + value.toString()).toString();
                s = "[" + s + "]";
                if (j == 0)
                    ret.append(" | ").append(s).append(" ");
                else if ((j + 1) % 3 == 0)
                    ret.append(s).append(" | ");
                else ret.append(s).append(" ");
            }
            ret.append("\n");
            if ((i + 1) % 3 == 0)
                ret.append("_____________________________________________").append("\n");
        }
        return ret.toString();
    }

    public void initialize() {
        for (int i = 0; i < board.length; i++) {
            for (int j = 0; j < board.length; j ++) {
                board[i][j] = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));
            }
        }
    }

    public boolean collapsed() {
        for (List[] lists : board) {
            for (int j = 0; j < board.length; j++) {
                if (lists[j].size() != 1) return false;
            }
        }
        return true;
    }

    public void set(int row, int col, int value) {
        removeFromRow(row, value);
        removeFromColumn(col, value);
        removeFromBlock(row, col, value);
        // set as fact
        board[row][col] = new ArrayList<>(Arrays.asList(value));
    }

    public boolean next(List<SudokuGrid.Validity> validities) {
        return validities.get(0) == SudokuGrid.Validity.ROW_VALID
                && validities.get(1) == SudokuGrid.Validity.COLUMN_VALID
                && validities.get(2) == SudokuGrid.Validity.BLOCK_VALID;
    }

    public List<Validity> valid(int row, int col, int value) {
        if (withinBounds(row, col) && board[row][col].size() > 1) {
            List<Validity> validities = new ArrayList<>();

            List<Integer>[] r = row(row);
            List<Integer>[] c = column(col);
            List<Integer>[] b = block(row, col);

            validities.add(rowValid(r, value));
            validities.add(columnValid(c, value));
            validities.add(blockValid(b, value));

            return validities;
        }
        return new ArrayList<>(Arrays.asList(Validity.ROW_INVALID, Validity.COLUMN_INVALID, Validity.BLOCK_INVALID));
    }

    private Validity rowValid(List<Integer>[] a, int value) {
        for (List<Integer> integers : a) {
            if (integers.size() == 1) {
                if (integers.get(0) == value) {
                    return Validity.ROW_INVALID;
                }
            }

        }
        return Validity.ROW_VALID;
    }

    private Validity columnValid(List<Integer>[] a, int value) {
        for (List<Integer> integers : a) {
            if (integers.size() == 1) {
                if (integers.get(0) == value)
                    return Validity.COLUMN_INVALID;
            }
        }
        return Validity.COLUMN_VALID;
    }

    private Validity blockValid(List<Integer>[] a, int value) {
        for (List<Integer> integers : a) {
            if (integers.size() == 1) {
                if (integers.get(0) == value)
                    return Validity.BLOCK_INVALID;
            }
        }
        return Validity.BLOCK_VALID;
    }

    public List<Integer> retrieve(int row, int col) {
        if (withinBounds(row, col))
            return board[row][col];
        return new ArrayList<>();
    }

    private void removeFromBlock(int rowStart, int colStart, int value) {
        int[] rbCoords = realBlockCoords(rowStart, colStart);
        if (rbCoords != null) {
            for (int row = rbCoords[0]; row < rbCoords[0] + 3; row++) {
                List[] a = board[row];
                int j = rbCoords[1];

                List a1 = a[j++];
                List a2 = a[j++];
                List a3 = a[j];

                int index1 = a1.indexOf(value);
                int index2 = a2.indexOf(value);
                int index3 = a3.indexOf(value);

                if (index1 != -1) {
                    a1.remove(index1);
                }
                if (index2 != -1) {
                    a2.remove(index2);
                }
                if (index3 != -1) {
                    a3.remove(index3);
                }
            }
        }
    }

    private void removeFromColumn(int col, int value) {
        if (withinBounds(0, col)) {
            for (List[] lists : board) {
                if (lists[col].size() > 1) {
                    int index = lists[col].indexOf(value);
                    if (index != -1) {
                        lists[col].remove(index);
                    }
                }
            }
        }
    }

    private void removeFromRow(int row, int value) {
        if (withinBounds(row, 0)) {
            for (int j = 0; j < board.length; j++) {
                if (board[row][j].size() > 1) {
                    int index = board[row][j].indexOf(value);
                    if (index != -1) {
                        board[row][j].remove(index);
                    }
                }
            }
        }
    }

    public int size() {
        return board.length;
    }

    private boolean withinBounds(int xpos, int ypos) {
        return xpos >= 0 && ypos >= 0 && xpos < board.length && ypos < board.length;
    }

    private int[] realBlockCoords(int row, int col) {
        int r = row;
        int c = col;
        if (withinBounds(row, col)) {
            while (r % 3 != 0 || c % 3 != 0) {
                if (r % 3 != 0)
                    r--;
                if (c % 3 != 0)
                    c--;
            }
            return new int[]{r, c};
        }
        return null;
    }

    public List<Integer>[] row(int row) {
        if (withinBounds(row, 0))
            return (List<Integer>[]) board[row];
        return null;
    }

    public List<Integer>[] column(int col) {
        if (withinBounds(0, col)) {
            AtomicInteger i = new AtomicInteger();
            return Arrays.stream(board).reduce(new List[board.length], (a1, a2) -> {
                a1[i.get()] = a2[col];
                i.getAndIncrement();
                return a1;
            });
        }
        return null;
    }

    private List<Integer>[] block(int rowStart, int colStart) {
        int[] rbCoords = realBlockCoords(rowStart, colStart);
        if (rbCoords != null) {
            List<Integer>[] a1 = new ArrayList[board.length];
            int i = 0;
            for (int row = rbCoords[0]; row < rbCoords[0] + 3; row++) {
                List<Integer>[] a2 = board[row];
                int j = rbCoords[1];
                a1[i++] = a2[j++];
                a1[i++] = a2[j++];
                a1[i++] = a2[j];
            }
            return a1;
        }
        return null;
    }
}
