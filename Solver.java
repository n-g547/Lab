package uebung_parallelisierung.sequentiell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.util.ArrayDeque;
import java.util.Arrays;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

final public class Solver extends JPanel  {
	
	private static final long serialVersionUID = 1L;

	// The default size of the labyrinth (i.e. unless program is invoked with size arguments):
	private static final int DEFAULT_WIDTH_IN_CELLS = 100;
	private static final int DEFAULT_HEIGHT_IN_CELLS = 100;

	private static final int N_RUNS_HALF = 5;  // #runs will be 2*N_RUNS_HALF + 1
	
	// The grid defining the structure of the labyrinth
	private final Labyrinth labyrinth;
	
	// For each cell in the labyrinth: Has solve() visited it yet?
	private boolean[][] visited; // initialized in solve()
	
	private Point[] solution = null; // set to solution path once that has been computed

	public Solver(Labyrinth labyrinth) {
		this.labyrinth = labyrinth; 
	}
	
    public Solver(int width, int height) {
    	this(new Labyrinth(width, height));
	}

	private boolean visitedBefore(Point p) {
		return visited[p.getX()][p.getY()];
	}
	
	private void visit(Point p) {
		visited[p.getX()][p.getY()] = true;
	}

	/**
	 * @return Returns a path through the labyrinth from start to end as an array, or null if no solution exists
	 */
	public Point[] solve() {

		// Initialize the search state: This must be done here to be part of the timing measurement
		
		Point current = labyrinth.getStart();
		ArrayDeque<Point> pathSoFar = new ArrayDeque<Point>();  // Path from start to just before current
		visited = new boolean[labyrinth.getWidth()][labyrinth.getHeight()]; // initially all false
		ArrayDeque<PointAndDirection> backtrackStack = new ArrayDeque<PointAndDirection>();
			// Used as a stack: Branches not yet taken; solver will backtrack to these branching points later
			// TODO: Is it faster to allocate backtrackStack with width*height elements right away?

		// Search:
		
		while (!labyrinth.isDestination(current)) {
			Point next = null;
			visit(current);

			// Use first random unvisited neighbor as next cell, push others on the backtrack stack: 
			Direction[] dirs = Direction.values();
			for (Direction directionToNeighbor: dirs) {
				Point neighbor = current.getNeighbor(directionToNeighbor);
				if (   labyrinth.hasPassage(current, directionToNeighbor)
					&& !visitedBefore(neighbor) 
					&& (   !labyrinth.isBlindAlley(neighbor, directionToNeighbor.opposite)
				        || labyrinth.isDestination(neighbor))) {
					if (next == null) // 1st unvisited neighbor
						next = neighbor;
					else {
						// 2nd or higher unvisited neighbor: Save neighbor as starting cell for a later backtracking
						backtrackStack.push(new PointAndDirection(neighbor, directionToNeighbor.opposite));
						// System.out.println("Pushing " + neighbor + " to the backtracking stack.");
					}
				}
			}
			// Advance to next cell, if any:
			if (next != null) {
				// System.out.println("Advancing from " + current + " to " + next);
				pathSoFar.addLast(current);
				current = next;
			} else { 
				// current has no unvisited neighbor: Backtrack, if possible
				if (backtrackStack.isEmpty())
					return null; // No more backtracking avaible: No solution exists

				// Backtrack: Continue with cell saved at latest branching point:
				PointAndDirection pd = backtrackStack.pop();
				current = pd.getPoint();
				Point branchingPoint = current.getNeighbor(pd.getDirectionToBranchingPoint());
				// System.out.println("Backtracking to " +  branchingPoint);
				// Remove the dead end from the top of pathSoFar, i.e. all cells after branchingPoint:
				while (!pathSoFar.peekLast().equals(branchingPoint)) {
					// System.out.println("    Going back before " + pathSoFar.peekLast());
					pathSoFar.removeLast();
				}
			}
		}
		pathSoFar.addLast(current);
		 // Point[0] is only for making the return value have type Point[] (and not Object[]):
		return pathSoFar.toArray(new Point[0]); 
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