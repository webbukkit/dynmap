/* MySQLSource class wrapper to expose protected properties */

import java.util.List;

public class DMMySQLSource extends MySQLSource {
	public List<Warp> getAllWarps() {
		return this.warps;
	}
	
	public List<Warp> getAllHomes() {
		return this.homes;
	}
}
