package uebung_parallelisierung.sequentiell;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.*;


final public class SolverP extends JPanel {

    private static final long serialVersionUID = 1L;

    // The default size of the labyrinth (i.e. unless program is invoked with size arguments):
    private static final int DEFAULT_WIDTH_IN_CELLS = 100;
    private static final int DEFAULT_HEIGHT_IN_CELLS = 100;

    private static final int N_RUNS_HALF = 5;  // #runs will be 2*N_RUNS_HALF + 1

    // The grid defining the structure of the labyrinth
    private final Labyrinth labyrinth;

    // For each cell in the labyrinth: Has solve() visited it yet?
    private volatile boolean[][] visited; // initialized in solve()

    private volatile Point[] solution = null; // set to solution path once that has been computed

    public SolverP(Labyrinth labyrinth) {
        this.labyrinth = labyrinth;
    }

    public SolverP(int width, int height) {
        this(new Labyrinth(width, height));
    }

    private boolean visitedBefore(Point p) {
        return visited[p.getX()][p.getY()];
    }

    private void visit(Point p) {
//        visited[p.getX()][p.getY()].compareAndSet(false, true);
        visited[p.getX()][p.getY()] = true;
    }

    private class SolverTask extends RecursiveTask<Point[]> {
        private Point current;
        private ArrayDeque<Point> pathSoFar;
        private ArrayDeque<PointAndDirection> backtrackStack;

        SolverTask(Point current, ArrayDeque<Point> pathSoFar, ArrayDeque<PointAndDirection> backtrackStack) {
            this.current = current;
            this.pathSoFar = pathSoFar;
            this.backtrackStack = backtrackStack;
        }
        @Override
        protected Point[] compute() {
            if (labyrinth.isDestination(current)) {
                pathSoFar.addLast(current);
                return pathSoFar.toArray(new Point[0]);
            }

            visit(current);
            List<SolverTask> tasks = new ArrayList<>();
            Point next = null;
            Direction[] dirs = Direction.values();

            for(Direction directionToNeighbor : dirs) {
                Point neighbor = current.getNeighbor(directionToNeighbor);
                if ( labyrinth.hasPassage(current, directionToNeighbor) && !visitedBefore(neighbor)
                        && ( !labyrinth.isBlindAlley(neighbor, directionToNeighbor.opposite)  || labyrinth.isDestination(neighbor))  ) {
                    if (next == null) {
                        next = neighbor;
                    } else {
                        tasks.add(new SolverTask(neighbor, new ArrayDeque<>(pathSoFar), new ArrayDeque<>(backtrackStack)));
                    }
                }
            }

            if (next != null) {
                pathSoFar.addLast(current);
                SolverTask mainTask = new SolverTask(next, pathSoFar, backtrackStack);
                tasks.add(mainTask);
                invokeAll(tasks);
                for (SolverTask task : tasks) {
                    Point[] result = task.join();
                    if (result != null) {
                        return result;
                    }
                }
            } else {
                if (!backtrackStack.isEmpty()) {
                    PointAndDirection pd = backtrackStack.pop();
                    Point backtrackPoint = pd.getPoint().getNeighbor(pd.getDirectionToBranchingPoint());
                    while(!pathSoFar.isEmpty() && !pathSoFar.peekLast().equals(backtrackPoint)) {
                        pathSoFar.removeLast();
                    }
                    SolverTask backtrackTask = new SolverTask(pd.getPoint(), pathSoFar, backtrackStack);
                    return backtrackTask.compute();
                }
            }
            return null;
        }
    }


    /**
     * @return Returns a path through the labyrinth from start to end as an array, or null if no solution exists
     */
    public Point[] solve() {
        // Initialize the search state: This must be done here to be part of the timing measurement

        Point current = labyrinth.getStart();
        visited = new boolean[labyrinth.getWidth()][labyrinth.getHeight()]; // initially all false

        ForkJoinPool forkJoinPool = new ForkJoinPool();
        SolverTask solverTask = new SolverTask(current, new ArrayDeque<>(), new ArrayDeque<>());
        solution = forkJoinPool.invoke(solverTask);
        return solution;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        // draw white background
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, labyrinth.getWidth()*labyrinth.cell_size_pixels(), labyrinth.getHeight()*labyrinth.cell_size_pixels());

        // draw solution path, if available
        if (solution  != null) {
            graphics.setColor(Color.YELLOW);
            for (Point p: solution)
/*				// fill only white area between the walls instead of whole cell:
				graphics.fillRect(p.getX()*CELL_PX+HALF_WALL_PX, p.getY()*CELL_PX+HALF_WALL_PX,
											CELL_PX-2*HALF_WALL_PX, CELL_PX-2*HALF_WALL_PX);
*/
                graphics.fillRect(p.getX()*labyrinth.cell_size_pixels(), p.getY()*labyrinth.cell_size_pixels(),
                        labyrinth.cell_size_pixels(), labyrinth.cell_size_pixels());
        }
        // draw walls
        labyrinth.display(graphics);
    }

    public void printSolution() {
        System.out.print("Solution: ");
        for (Point p: solution)
            System.out.print(p);
        System.out.println();
    }

    public void displaySolution() {
        repaint();
    }



    //TODO
    private static Solver makeAndSaveSolver(String[] args) {

        // Construct solver: Either read it from a file, or create a new one
        if (args.length >= 1 && args[0].endsWith(".ser")) {

            // 1st argument is name of file with serialized labyrinth: Ignore other arguments
            // and create a solver for the labyrinth from that file:
            ObjectInputStream ois;
            try {
                ois = new ObjectInputStream(new FileInputStream(args[0]));
                Labyrinth labyrinth = (Labyrinth)ois.readObject();
                ois.close();
                return new Solver(labyrinth);
            } catch (Exception e) {
                System.out.println(e);
                return null;
            }
        } else {
            // Create solver for new, random labyrinth:

            int width = args.length >= 1 ? (Integer.parseInt(args[0])) : DEFAULT_WIDTH_IN_CELLS;
            int height = args.length >= 2 ? (Integer.parseInt(args[1])) : DEFAULT_HEIGHT_IN_CELLS;

            Solver solver = new Solver(width, height);

            // Save labyrinth to file (may be reused in future program executions):
            try {
                ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("labyrinth.ser"));
                oos.writeObject(solver.labyrinth);
                oos.close();
            } catch (Exception e) {
                System.out.println(e);
            }

            return solver;
        }
    }

    //Hier wird das Labyrinth gezeichnet
    private static void displayLabyrinth(Solver solver) {
        JFrame frame = new JFrame("Sequential labyrinth solver");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // TODO: Window is initially displayed somewhat smaller than
        // the indicated frame size, therefore use width+5 and height+5:
        frame.setSize((solver.labyrinth.getWidth()+5) * solver.labyrinth.cell_size_pixels(),
                (solver.labyrinth.getHeight()+5) * solver.labyrinth.cell_size_pixels());

        // Put a scroll pane around the labyrinth frame if the latter is too large
        // (by Joern Lenselink)
        Dimension displayDimens = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().getSize();
        Dimension labyrinthDimens = frame.getSize();
        if(labyrinthDimens.height > displayDimens.height) {
            JScrollPane scroll = new JScrollPane();
            solver.setBackground(Color.LIGHT_GRAY);
            frame.getContentPane().add(scroll);
            JPanel borderlayoutpanel = new JPanel();
            borderlayoutpanel.setBackground(Color.darkGray);
            scroll.setViewportView(borderlayoutpanel);
            borderlayoutpanel.setLayout(new BorderLayout(0, 0));

            JPanel columnpanel = new JPanel();
            borderlayoutpanel.add(columnpanel, BorderLayout.NORTH);
            columnpanel.setLayout(new GridLayout(0, 1, 0, 1));
            columnpanel.setOpaque(false);
            columnpanel.setBackground(Color.darkGray);

            columnpanel.setSize(labyrinthDimens.getSize());
            columnpanel.setPreferredSize(labyrinthDimens.getSize());
            columnpanel.add(solver);
        } else {
            // No scroll pane needed:
            frame.getContentPane().add(solver);
        }

        frame.setVisible(true); // will draw the labyrinth (without solution)
    }






    /**
     *
     * @param args If the first argument is a file name ending in .ser, the serialized labyrinth in that file
     * is used; else the first two arguments are optional numbers giving the width and height of a new
     * labyrinth to be constructed. Then the labyrinth is solved and displayed (unless too large).
     * This is run a certain number of times and then the median run time is printed.
     */
    public static void main(String[] args) {
        long[] runTimes = new long[2*N_RUNS_HALF + 1];

        for (int run = 0; run < 2*N_RUNS_HALF + 1; ++run) {

            Solver solver = makeAndSaveSolver(args);
            if (solver.labyrinth.smallEnoughToDisplay()) {
                displayLabyrinth(solver);
            }

            long startTime = System.currentTimeMillis();
            solver.solution = solver.solve();
            long endTime = System.currentTimeMillis();

            if (solver.solution == null)
                System.out.println("No solution exists.");
            else {
                System.out.println("Computed sequential solution of length " + solver.solution.length + " to labyrinth of size " +
                        solver.labyrinth.getWidth() + "x" + solver.labyrinth.getHeight() + " in " + (endTime - startTime) + "ms.");

                runTimes[run] = endTime - startTime;

                if (solver.labyrinth.smallEnoughToDisplay()) {
                    solver.displaySolution();
                    solver.printSolution();
                }

                if (solver.labyrinth.checkSolution(solver.solution))
                    System.out.println("Solution correct :-)");
                else
                    System.out.println("Solution incorrect :-(");
            }
        }
        Arrays.sort(runTimes);
        System.out.println("Median run time was " + runTimes[N_RUNS_HALF] + " ms.");
    }
}
