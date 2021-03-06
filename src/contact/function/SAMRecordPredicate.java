package contact.function;

import htsjdk.samtools.fork.SAMRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Static methods returning SAMRecord predicates
 * @author prussell
 *
 */
public class SAMRecordPredicate {
	
	/**
	 * Default predicates
	 */
	public static final List<Predicate<SAMRecord>> DEFAULT = defaultPredicates();
	
	/**
	 * @return Default predicates
	 */
	private static final List<Predicate<SAMRecord>> defaultPredicates() {
		List<Predicate<SAMRecord>> rtrn = new ArrayList<Predicate<SAMRecord>>();
		rtrn.add(minMappingQuality(2));
		return rtrn;
	}
	
	/**
	 * True if the record is marked as the primary mapping
	 * @author prussell
	 *
	 */
	public static final Predicate<SAMRecord> isPrimaryMapping() {
		
		return new Predicate<SAMRecord>() {
			@Override
			public boolean test(SAMRecord t) {
				return !t.getNotPrimaryAlignmentFlag();
			}
		};
		
	}
	
	/**
	 * True if the record has mapping quality greater than or equal to a minimum
	 * @author prussell
	 *
	 */
	public static final Predicate<SAMRecord> minMappingQuality(int minMapq) {
		
		return new Predicate<SAMRecord>() {

			@Override
			public boolean test(SAMRecord record) {
				return record.getMappingQuality() >= minMapq;
			}
			
		};
		
	}
	
}
