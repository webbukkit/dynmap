package org.dynmap.utils;

import org.dynmap.Log;
import org.dynmap.renderer.DynmapBlockState;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Utility class used for parsing block name and block state identity from lines in model and texture files
public class BlockStateParser {
	private HashMap<DynmapBlockState, BitSet> basestates;
	private ArrayList<String> badtokens;
	private String modid;
	private int linenum;
	private boolean filtered;

	public BlockStateParser() {
	}

	/**
	 * Process new line into list of matching states Produce list of block states
	 * matching the fields in the provided line (specifically the portion after the
	 * '<type>:" prefix) at the stsrt of the line. Processes id=<block ID> or
	 * id=<nlock Name> or id=<modid>:<blockName> as block identifier data=<N> or
	 * data=<N>-<N2> as selecting matching states of identified blocks by ordered
	 * index of their block states (analog to metadata>
	 * state=<id:val>/<id2:val2>/... as selecting matching states (based on all
	 * states where given id=val values match)
	 * 
	 * @return success or no
	 */
	public boolean processLine(String modid, String line, int linenum, Map<String, Integer> varMap) {
		boolean success = true;
		String tokens[] = line.split(","); // Split on the commas
		this.basestates = new HashMap<DynmapBlockState, BitSet>();
		this.badtokens = new ArrayList<String>();
		this.modid = modid;
		this.linenum = linenum;
		this.filtered = false;
		// Loop through and process id= tokens
		for (String token : tokens) {
			int idx = token.indexOf("="); // Find equals
			if (idx < 0)
				continue; // Skip token without equals
			String fieldid = token.substring(0, idx); // Split off left of equals
			String args = token.substring(idx + 1); // And rest of token after equals
			// Just do IDs first (need block names to get to states
			if (fieldid.equals("id")) {
				if (!handleBlockName(args)) {
					badtokens.add(token);
					success = false;
				}
			}
		}
		// Loop through and process data= and state= tokens
		for (String token : tokens) {
			int idx = token.indexOf("="); // Find equals
			if (idx < 0)
				continue; // Skip token without equals
			String fieldid = token.substring(0, idx); // Split off left of equals
			String args = token.substring(idx + 1); // And rest of token after equals
			// Check for data=
			if (fieldid.equals("data")) {
				if (!handleBlockData(args, varMap)) {
					badtokens.add(token);
					success = false;
				}
			}
			// If state=
			else if (fieldid.equals("state")) {
				if (!handleBlockState(args)) {
					badtokens.add(token);
					success = false;
				}
			}
		}
		// If unfiltered, add all states for all blocks
		if (!filtered) {
			// Now loop through base states and add matching indexes
			for (DynmapBlockState bs : basestates.keySet()) {
				int cnt = bs.getStateCount();
				BitSet bits = basestates.get(bs);
				for (int idx = 0; idx < cnt; idx++) {
					bits.set(bs.getState(idx).stateIndex);
				}
			}
		}
		// Log.info(String.format("processLine(%s)=%b, basestates=%s", line, success,
		// basestates));
		return success;
	}

	// Return matching results from last processLine call
	public Map<DynmapBlockState, BitSet> getMatchingStates() {
		return basestates;
	}

	// Return bad tokens from last processLine call
	public List<String> getBadTokens() {
		return badtokens;
	}

	private boolean handleBlockName(String blockname) {
		char c = blockname.charAt(0);
		if (Character.isLetter(c) || (c == '%') || (c == '&')) {
			String orig = blockname;
			if ((c == '%') || (c == '&')) {
				blockname = blockname.substring(1);
			}
			if (blockname.indexOf(':') < 0) {
				blockname = modid + ":" + blockname;
			}
			// Now find the base block state
			DynmapBlockState bs = DynmapBlockState.getBaseStateByName(blockname);
			// Bad if we failed
			if (bs == null) {
				Log.warning(String.format("id=%s on line %d does not match valid blockName", orig, linenum));
				return false;
			}
			basestates.put(bs, new BitSet());
			return true;
		} else { // Numbers not support anymore
			Log.warning(String.format("id=%s on line %d invalid format (numbers not supported anymore)", blockname,
					linenum));
			return false;
		}
	}

	private boolean handleBlockData(String data, Map<String, Integer> varMap) {
		try {
			if (data.equals("*")) {
				filtered = false;
			} else {
				int split = data.indexOf('-'); // See if range of data
				int m0, m1;
				if (split > 0) {
					String start = data.substring(0, split);
					String end = data.substring(split + 1);
					m0 = getIntValue(varMap, start);
					m1 = getIntValue(varMap, end);
				} else {
					m0 = m1 = getIntValue(varMap, data);
				}
				filtered = true;
				// Now loop through base states and add matching indexes
				for (DynmapBlockState bs : basestates.keySet()) {
					int cnt = bs.getStateCount();
					BitSet bits = basestates.get(bs);
					for (int idx = m0; (idx <= m1) && (idx < cnt); idx++) {
						bits.set(bs.getState(idx).stateIndex);
					}
					if ((m1 >= cnt) || (m0 >= cnt)) {
						Log.warning(String.format("data=%s on line %d exceeds state count for %s", data, linenum,
								bs.blockName));
					}
				}
			}
			return true;
		} catch (NumberFormatException x) {
			return false;
		}
	}

	private boolean handleBlockState(String data) {
		boolean success = true;
		if (data.equals("*")) {
			filtered = false;
		} else {
			String[] split = data.split("/"); // Split on pairs
			String[] attribs = new String[split.length];
			String[] vals = new String[split.length];
			for (int i = 0; i < split.length; i++) {
				String[] av = split[i].split(":");
				if (av.length == 2) {
					attribs[i] = av[0];
					vals[i] = av[1];
				} else {
					success = false;
				}
			}
			filtered = true;
			// Now loop through base states and add matching indexes
			if (success) {
				for (DynmapBlockState bs : basestates.keySet()) {
					int cnt = bs.getStateCount();
					BitSet bits = basestates.get(bs);
					for (int idx = 0; idx < cnt; idx++) {
						DynmapBlockState s = bs.getState(idx);
						boolean match = true;
						for (int i = 0; match && (i < attribs.length); i++) {
							if (!s.isStateMatch(attribs[i], vals[i])) {
								match = false;
							}
						}
						if (match) {
							bits.set(idx); // Set matching state
						}
					}
				}
			}
		}
		if (!success) {
			Log.warning(String.format("Bad block state %s for line %s", data, linenum));
		}
		return success;
	}

	private static Integer getIntValue(Map<String, Integer> vars, String val) throws NumberFormatException {
		char c = val.charAt(0);
		if (Character.isLetter(c) || (c == '%') || (c == '&')) {
			int off = val.indexOf('+');
			int offset = 0;
			if (off > 0) {
				offset = Integer.valueOf(val.substring(off + 1));
				val = val.substring(0, off);
			}
			Integer v = vars.get(val);
			if (v == null) {
				if ((c == '%') || (c == '&')) { // block/item unique IDs
					vars.put(val, 0);
					v = 0;
				} else {
					throw new NumberFormatException("invalid ID - " + val);
				}
			}
			if ((offset != 0) && (v.intValue() > 0))
				v = v.intValue() + offset;
			return v;
		} else {
			return Integer.valueOf(val);
		}
	}
}
