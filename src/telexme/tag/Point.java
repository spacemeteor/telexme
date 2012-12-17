package telexme.tag;

public class Point {
	private Coord[] array = new Coord[3]; // [0]=x, [1]=y, [2]=z

	public Point() {
		for (int i = 0; i < array.length; i++) {
			array[i] = new Coord();
		}
	}

	public void set(int i, Coord coord) {
		array[i] = coord;
	}

	public Coord get(int i) {
		return array[i];
	}
	
	public void copyFrom(Point other) {
		for (int i = 0; i < array.length; i++) {
			array[i] = new Coord();		// Coords are mutable
			array[i].copyFrom(other.array[i]);
		}
	}
}
