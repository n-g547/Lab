package uebung_parallelisierung.sequentiell;
/*
 * A labyrinth generated using the depth-first algorithm
 * (www.astrolog.org/labyrnth/algrithm.htm), with a start point and end point
 * for a search and with a display (unless too large) as ASCII graphics and
 * Swing graphics.
 * Source of labyrinth representation and ASCII output generation:
 * http://rosettacode.org/wiki/Maze#Java
 */



import java.awt.Color;
import java.awt.Graphics;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;

public final class Labyrinth implements Serializable{
    private static final long serialVersionUID = 1L;

    /**
     * Serialized state of a labyrinth with size, passages, start and end
     * (without search state) and with all information defining its graphic
     * and textual display.
     */

    private final int width;   // total number of cells in x direction
    private final int height;  // total number of cells in y direction
    private final Point start; // starting point of the search
    private final Point end;   // end point of the search

    private final byte[][] passages;
    /*
     *  Each array element represents a cell in the labyrinth with the passages possible from
     *  this cell. Its four least significant bits are interpreted as one flag for each direction
     *  (see enum Direction for which bit means which direction) indicating whether
     *  there is a passage from this cell in that direction (note that passages
     *  and walls are not cells, but represented indirectly by these flags).
     *  Initially all cells are 0, i.e. have no passage from them (i.e. surrounded
     *  by walls on all their four sides). Note that two-way passages appear as opposite
     *  bits in both the source and destination cell; thus, this data structure supports
     *  one-way passages, too, by setting a bit in the source cell only.
     */

    // When generating the labyrinth and considering whether to create a passage to some neighbor cell, create a
    // passage to a cell that is already accessible on another path (i.e. create a cycle) with this probability:
    private static final double CYCLE_CREATION_PROBABILITY = 0.0;

    private static final int  CELL_PX = 10;  // width and length of the labyrinth cells in pixels
    private static final int  HALF_WALL_PX = 2;  // thickness/2 of the labyrinth walls in pixels
    // labyrinths with more pixels than this (in one or both directions) will not be graphically displayed:
    private static final int MAX_PX_TO_DISPLAY = 1000;

    public Labyrinth(int width, int height) {
        this.width = width;
        this.height = height;

        // Always start in the center of the labyrinth:
        start = new Point(width/2, height/2);

        // Randomly pick a cell on the boundary as the end point:
        int endIndex = (int)((2*width + 2*height) * Math.random());
        int endX;
        int endY;
        // Try the four edges of the grid, starting at the upper edge,
        // proceeding clockwise to the left edge:
        if (endIndex < width) { // upper edge
            endX = endIndex;
            endY = 0;
        } else {
            if (endIndex < width + height) { // right edge
                endX = width-1;
                endY = endIndex - width;
            } else {
                if (endIndex < 2*width + height) { // lower edge
                    endX = endIndex - width - height;
                    endY = height-1;
                } else { // left edge
                    endX = 0;
                    endY = endIndex - 2*width - height;
                }
            }
        }
        end = new Point(endX, endY);

        passages = new byte[width][height]; // initially all 0 (see comment at declaration of passages)
        makePassages();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Point getStart() {
        return start;
    }

    public boolean hasPassage(Point from, Direction directionToNeighbor) {
        return contains(from)  && (passages[from.getX()][from.getY()] & directionToNeighbor.bit) != 0;
    }

    public boolean hasPassage(Point from, Point to) {
        if (!contains(from) ||  !contains(to)) {
            return false;
        }
        if (from.getNeighbor(Direction.N).equals(to))
            return (passages[from.getX()][from.getY()] & Direction.N.bit) != 0;
        if (from.getNeighbor(Direction.S).equals(to))
            return (passages[from.getX()][from.getY()] & Direction.S.bit) != 0;
        if (from.getNeighbor(Direction.E).equals(to))
            return (passages[from.getX()][from.getY()] & Direction.E.bit) != 0;
        if (from.getNeighbor(Direction.W).equals(to))
            return (passages[from.getX()][from.getY()] & Direction.W.bit) != 0;
        return false;  // To suppress warning about undefined return value
    }

    public boolean contains(Point p) {
        return 0 <= p.getX() && p.getX() < width &&
                0 <= p.getY() && p.getY() < height;
    }

    public boolean isDestination(Point p) {
        return p.equals(end);
    }

    /**
     * Return whether <code>p</code>, when coming from <code>fromDir</code>, is a blind alley.
     */
    public boolean isBlindAlley(Point p, Direction fromDir) {
        int directionBitsExceptFromDir = Direction.allDirectionBits & ~fromDir.bit;
        return (passages[p.getX()][p.getY()] & directionBitsExceptFromDir) == 0;
    }

    /**
     * Generate a labyrinth (with or without cycles, depending on CYCLE_CREATION_PROBABILITY)
     * using the depth-first algorithm (www.astrolog.org/labyrnth/algrithm.htm (sic!))
     */

    private void makePassages() {
        ArrayDeque<Point> pointsToDo = new ArrayDeque<Point>();
        Point current;
        pointsToDo.push(getStart());
        while (!pointsToDo.isEmpty()) {
            current = pointsToDo.pop();
            int cx = current.getX();
            int cy = current.getY();
            Direction[] dirs = Direction.values();
            Collections.shuffle(Arrays.asList(dirs));
            // For all unvisited neighboring cells in random order:
            // Make a passage from the current cell to that neighbor
            for (Direction dir : dirs) {
                // Pick random neighbor of current cell as new cell (nx, ny)
                Point neighbor = current.getNeighbor(dir);
                int nx = neighbor.getX();
                int ny = neighbor.getY();

                if (contains(neighbor) // If neighbor is still in the labyrinth ...
                        && 	(	 passages[nx][ny] == 0 // ... and has no passage yet, i.e. has not been visited yet during generation
                        || Math.random() < CYCLE_CREATION_PROBABILITY )) {  // ... or creating a cycle is OK

                    // Make a two-way passage, i.e. from current to neighbor and from neighbor to current:
                    passages[cx][cy] |= dir.bit;
                    passages[nx][ny] |= dir.opposite.bit;

                    // Remember to continue from this neighbor later on
                    pointsToDo.push(neighbor);
                }
            }
        }
    }


    public void print() {
        System.out.println("Labyrinth with start " + start + " and end " + end);
        for (int i = 0; i < height; i++) {
            // draw the north edges
            for (int j = 0; j < width; j++) {
                System.out.print((passages[j][i] & Direction.N.bit) == 0 ? "+---" : "+   ");
            }
            System.out.println("+");
            // draw the west edges
            for (int j = 0; j < width; j++) {
                System.out.print((passages[j][i] & Direction.W.bit) == 0 ? "|   " : "    ");
            }
            // draw the far east edge
            System.out.println("|");
        }
        // draw the bottom line
        for (int j = 0; j < width; j++) {
            System.out.print("+---");
        }
        System.out.println("+");
    }

    public int cell_size_pixels() {
        return CELL_PX;
    }

    public boolean smallEnoughToDisplay() {
        return width*CELL_PX <= MAX_PX_TO_DISPLAY && height*CELL_PX <= MAX_PX_TO_DISPLAY;
    }

    public void display(Graphics graphics) {
        // draw start and end cell in special colors (covering start and end cell of the solution path)
        graphics.setColor(Color.RED);
        graphics.fillRect(start.getX()*CELL_PX, start.getY()*CELL_PX, CELL_PX, CELL_PX);
        graphics.setColor(Color.GREEN);
        graphics.fillRect(end.getX()*CELL_PX, end.getY()*CELL_PX, CELL_PX, CELL_PX);

        // draw black walls (covering part of the solution path)
        graphics.setColor(Color.BLACK);
        for(int x = 0; x < width; ++x) {
            for(int y = 0; y < height; ++y) {
                // draw north edge of each cell (together with south edge of cell above)
                if ((passages[x][y] & Direction.N.bit) == 0)
                    // y-HALF_WALL_PX will be half out of labyrinth for x==0 row,
                    // but that does not hurt the picture thanks to automatic cropping
                    graphics.fillRect(x*CELL_PX, y*CELL_PX-HALF_WALL_PX, CELL_PX, 2*HALF_WALL_PX);
                // draw west edge of each cell (together with east edge of cell to the left)
                if ((passages[x][y] & Direction.W.bit) == 0)
                    // x-HALF_WALL_PX will be half out of labyrinth for y==0 column,
                    // but that does not hurt the picture thanks to automatic cropping
                    graphics.fillRect(x*CELL_PX-HALF_WALL_PX, y*CELL_PX, 2*HALF_WALL_PX, CELL_PX);
            }
        }
        // draw east edge of labyrinth
        graphics.fillRect(width*CELL_PX, 0, HALF_WALL_PX, height*CELL_PX);
        // draw south edge of labyrinth
        graphics.fillRect(0, height*CELL_PX-HALF_WALL_PX, width*CELL_PX, HALF_WALL_PX);
    }


    public boolean checkSolution(Point solution[]) {
        Point from = solution[0];
        if (!from.equals(start)) {
            System.out.println("checkSolution fails because the first cell is" + from + ", but not  " + start);
            return false;
        }

        for (int i = 1; i < solution.length; ++i) {
            Point to = solution[i];
            if (!hasPassage(from, to)) {
                System.out.println("checkSolution fails because there is no passage from " + from + " to " + to);
                return false;
            }
            from = to;
        }
        if (!from.equals(end)) {
            System.out.println("checkSolution fails because the last cell is" + from + ", but not  " + end);
            return false;
        }
        return true;
    }
}
