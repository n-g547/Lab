package uebung_parallelisierung.sequentiell;


import java.io.Serializable;

final class Point implements Serializable {
    private static final long serialVersionUID = 1L;
    final int x, y;
    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    int getX() { return x; }
    int getY() { return y; }

    final Point getNeighbor(Direction dir) {
        return new Point(x+dir.dx, y+dir.dy);
    }

    @Override
    public String toString() {
        return "("+x+", "+y+")";
    }

    @Override
    public boolean equals(Object other) {
        if (other == this)
            return true;
        if (other.getClass() != this.getClass())
            return false;
        Point p = (Point)other;
        return x == p.x && y == p.y;
    }

    @Override
    public int hashCode() {
        return 3001*x+y;  // 3001 is prime
    }
}
