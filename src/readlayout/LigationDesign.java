package readlayout;

import java.util.HashMap;
import java.util.Map;

/**
 * A design for the layout of the reads
 * @author prussell
 *
 */
public enum LigationDesign {
	
	
	/**
	 * Design from April 2016
	 * Read 1 contains RPM or DPM at known position
	 * Assume data have been divided into RNA/DNA and RPM/DPM removed before running this
	 * Barcodes are in read2: Y, even, odd, even, odd
	 */
	PAIRED_DESIGN_APRIL_2016_5BARCODE("paired_design_april_2016_5_barcode"),
	
	/**
	 * Design from April 2016
	 * Read 1 contains RPM or DPM at known position
	 * Assume data have been divided into RNA/DNA and RPM/DPM removed before running this
	 * Barcodes are in read2: Y, even, odd, even
	 */
	PAIRED_DESIGN_APRIL_2016_4BARCODE("paired_design_april_2016_4_barcode");
	
	private LigationDesign(String name) {
		this.name = name;
	}
	
	private String name;
	private static Map<String, LigationDesign> fromName;
	
	// Save mapping of name to object
	static {
		fromName = new HashMap<String, LigationDesign>();
		for(LigationDesign ld : values()) {
			fromName.put(ld.toString(), ld);
		}
	}
	
	public String toString() {
		return name;
	}
	
	/**
	 * Create from name
	 * @param name Design name
	 * @return The design
	 */
	public static LigationDesign fromString(String name) {
		if(!fromName.containsKey(name)) {
			throw new IllegalArgumentException("Ligation design names: " + getNamesAsCommaSeparatedList());
		}
		return fromName.get(name);
	}
	
	/**
	 * @return Comma separated list of names
	 */
	public static String getNamesAsCommaSeparatedList() {
		LigationDesign[] values = LigationDesign.values();
		String rtrn = values[0].toString();
		for(int i = 1; i < values.length; i++) {
			rtrn += ", " + values[i].toString();
		}
		return rtrn;
	}
	
}
