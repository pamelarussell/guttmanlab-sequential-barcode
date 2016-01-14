package sequentialbarcode;


import static com.sleepycat.persist.model.Relationship.MANY_TO_ONE;
import fragmentgroup.FragmentGroup;
import fragmentgroup.BarcodedFragmentGroup;
import guttmanlab.core.util.StringParser;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import programs.BarcodedBamWriter;
import readelement.Barcode;
import readelement.BarcodeEquivalenceClass;
import readelement.BarcodeEquivalenceClassSet;
import readelement.BarcodeSet;
import readelement.ReadSequenceElement;
import readlayout.ReadLayout;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentMutableConfig;
import com.sleepycat.persist.EntityCursor;
import com.sleepycat.persist.PrimaryIndex;
import com.sleepycat.persist.SecondaryIndex;
import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.SecondaryKey;

import matcher.BitapMatcher;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMRecordIterator;
import net.sf.samtools.util.CloseableIterator;
import nextgen.core.alignment.Alignment;
import nextgen.core.annotation.Annotation;
import nextgen.core.annotation.BasicAnnotation;
import nextgen.core.berkeleydb.DatabaseEnvironment;
import nextgen.core.berkeleydb.DatabaseStore;
import nextgen.core.berkeleydb.JoinedEntityCursor;
import nextgen.core.model.AlignmentModel;

/**
 * A basic implementation of a barcoded fragment
 * @author prussell
 *
 */
@Entity(version=0)
public class BasicBarcodedFragment implements BarcodedFragment {
	@PrimaryKey
	protected String infoString;
	protected String id;
	protected String read1sequence;
	protected String read2sequence;
	protected String unpairedSequence;
	protected Annotation location;
	protected ReadLayout read1layout;
	protected ReadLayout read2layout;
	protected BarcodeSequence barcodes;
	@SecondaryKey(relate=MANY_TO_ONE)
	private String barcodeString;
	public static Logger logger = Logger.getLogger(BasicBarcodedFragment.class.getName());
	protected FragmentGroup fragmentGroup;
	
	/**
	 * @param fragmentId Fragment ID
	 * @param barcodeSignature Barcodes for the fragment
	 * @param mappedChr Mapped chromosome for the fragment
	 * @param mappedStart Mapped start
	 * @param mappedEnd Mapped end
	 */
	public BasicBarcodedFragment(String fragmentId, BarcodeSequence barcodeSignature, String mappedChr, int mappedStart, int mappedEnd) {
		this(fragmentId, barcodeSignature, new BasicAnnotation(mappedChr, mappedStart, mappedEnd));
	}
	
	/**
	 * @param fragmentId Fragment ID
	 * @param barcodeSignature Barcodes for the fragment
	 * @param mappedLocation Mapped location of the fragment
	 */
	public BasicBarcodedFragment(String fragmentId, BarcodeSequence barcodeSignature, Annotation mappedLocation) {
		id = StringParser.firstField(fragmentId);
		setBarcodes(barcodeSignature);
		location = mappedLocation;
		fragmentGroup = new BarcodedFragmentGroup(barcodes);
		infoString = getInfoString(id, location.getChr(), location.getStart(), location.getEnd());
	}
	
	/**
	 * Get number of barcodes
	 * @return Number of barcodes
	 */
	public int getNumBarcodes() {
		return barcodes.getNumBarcodes();
	}
	
	private void setBarcodes(BarcodeSequence bs) {
		barcodes = bs;
		barcodeString = barcodes.toString();
	}
	
	/**
	 * Get the info string that is used as the unique primary key for a mapping
	 * @param readID Read ID
	 * @param chr Mapped chromosome
	 * @param start Mapped start
	 * @param end Mapped end
	 * @return Info string
	 */
	public static String getInfoString(String readID, String chr, int start, int end) {
		return readID + ":" + chr + ":" + start + "-" + end;
	}
	
	/**
	 * Get fragment ID from sam record
	 * @param samRecord Sam record
	 * @return Fragment ID
	 */
	public static String getIdFromSamRecord(SAMRecord samRecord) {
		return StringParser.firstField(samRecord.getReadName());
	}
	
	/**
	 * Instantiate from a SAM record by reading location and attributes
	 * @param samRecord SAM record
	 */
	public BasicBarcodedFragment(SAMRecord samRecord) {
		
		String fragmentId = getIdFromSamRecord(samRecord);
		read1sequence = null;
		read2sequence = null;
		String seq = samRecord.getReadString();
		if(samRecord.getReadPairedFlag()) {
			if(samRecord.getFirstOfPairFlag()) {
				read1sequence = seq;
			} else if(samRecord.getSecondOfPairFlag()) {
				read2sequence = seq;
			} 
		} else {
			unpairedSequence = seq;
		}
		String barcodeString = samRecord.getStringAttribute(BarcodedBamWriter.BARCODES_SAM_TAG);
		BarcodeSequence barcodeSignature = BarcodeSequence.fromSamAttributeString(barcodeString);
		
		id = fragmentId;
		setBarcodes(barcodeSignature);
		location = new BasicAnnotation(samRecord);
		fragmentGroup = BarcodedFragmentGroup.createFromSAMRecord(samRecord);
		
		infoString = getInfoString(id, location.getChr(), location.getStart(), location.getEnd());
		
	}
	
	/**
	 * @param fragmentId Fragment ID
	 * @param read1seq Read1 sequence
	 * @param read2seq Read2 sequence
	 * @param barcodeSignature Barcodes for the fragment
	 */
	public BasicBarcodedFragment(String fragmentId, String read1seq, String read2seq, BarcodeSequence barcodeSignature) {
		this(fragmentId, read1seq, read2seq, barcodeSignature, null);
	}

	/**
	 * @param fragmentId Fragment ID
	 * @param read1seq Read1 sequence
	 * @param read2seq Read2 sequence
	 * @param barcodeSignature Barcodes for the fragment
	 * @param mappedLocation Mapped location of the fragment
	 */
	public BasicBarcodedFragment(String fragmentId, String read1seq, String read2seq, BarcodeSequence barcodeSignature, Annotation mappedLocation) {
		id = StringParser.firstField(fragmentId);
		read1sequence = read1seq;
		read2sequence = read2seq;
		setBarcodes(barcodeSignature);
		location = mappedLocation;
		fragmentGroup = new BarcodedFragmentGroup(barcodes);
		infoString = getInfoString(id, location.getChr(), location.getStart(), location.getEnd());
	}

	/**
	 * @param fragmentId Fragment ID
	 * @param read1seq Read1 sequence
	 * @param read2seq Read2 sequence
	 * @param layoutRead1 Read1 layout or null if not specified
	 * @param layoutRead2 Read2 layout or null if not specified
	 */
	public BasicBarcodedFragment(String fragmentId, String read1seq, String read2seq, ReadLayout layoutRead1, ReadLayout layoutRead2) {
		id = StringParser.firstField(fragmentId);
		read1sequence = read1seq;
		read2sequence = read2seq;
		read1layout = layoutRead1;
		read2layout = layoutRead2;
		fragmentGroup = new BarcodedFragmentGroup(barcodes);
	}
	
	public final BarcodeSequence getBarcodes() {
		return getBarcodes(null, null);
	}
	
	/**
	 * Get barcodes where we have already matched elements to the read(s)
	 * @param matchedEltsRead1 Matched elements for read 1 or null if identifying here for the first time or there is no read 1
	 * @param matchedEltsRead2 Matched elements for read 2 or null if identifying here for the first time or there is no read 2
	 */
	public final BarcodeSequence getBarcodes(List<List<ReadSequenceElement>> matchedEltsRead1, List<List<ReadSequenceElement>> matchedEltsRead2) {
		if(barcodes == null) {
			findBarcodes(matchedEltsRead1, matchedEltsRead2);
		}
		return barcodes;
	}
	
	public final void findBarcodes() {
		findBarcodes(null, null);
	}
	
	/**
	 * Identify which of a list of matched elements are barcodes, and append to the barcode sequence for this object
	 * @param readLayout Read layout
	 * @param readElements Matched elements
	 */
	private void findAndAppendBarcodes(ReadLayout readLayout, List<List<ReadSequenceElement>> readElements) {
		if(readElements != null) {
			for(int i = 0; i < readElements.size(); i++) {
				ReadSequenceElement parentElement = readLayout.getElements().get(i);
				Class<? extends ReadSequenceElement> cl = parentElement.getClass();
				if(cl.equals(Barcode.class) || cl.equals(BarcodeSet.class)) {
					for(ReadSequenceElement elt : readElements.get(i)) {
						barcodes.appendBarcode((Barcode)elt);
					}
					continue;
				}
				if(cl.equals(BarcodeEquivalenceClass.class) || cl.equals(BarcodeEquivalenceClassSet.class)) {
					for(ReadSequenceElement elt : readElements.get(i)) {
						BarcodeEquivalenceClass bec = (BarcodeEquivalenceClass) elt;
						barcodes.appendBarcode(bec.toBarcode());
					}
					continue;
				}
			}
		}
	}
	
	/**
	 * Find barcodes where we have already matched elements to the read(s)
	 * @param matchedEltsRead1 Matched elements for read 1 or null if identifying here for the first time or there is no read 1
	 * @param matchedEltsRead2 Matched elements for read 2 or null if identifying here for the first time or there is no read 2
	 */
	public final void findBarcodes(List<List<ReadSequenceElement>> matchedEltsRead1, List<List<ReadSequenceElement>> matchedEltsRead2) {
		barcodes = new BarcodeSequence();
			if(read1layout != null && read1sequence != null) {
				List<List<ReadSequenceElement>> read1elements = 
						matchedEltsRead1 == null ? new BitapMatcher(read1layout, read1sequence).getMatchedElements() : matchedEltsRead1;
				if(read1elements != null) {
					findAndAppendBarcodes(read1layout, read1elements);
				}
				read1elements = null;
			}
			if(read2layout != null && read2sequence != null) {
				List<List<ReadSequenceElement>> read2elements = 
						matchedEltsRead2 == null ? new BitapMatcher(read2layout, read2sequence).getMatchedElements() : matchedEltsRead2;
				if(read2elements != null) {
					findAndAppendBarcodes(read2layout, read2elements);
				}
				read2elements = null;
			}
		setBarcodes(barcodes);
	}
	
	public final String getId() {
		return id;
	}
	
	public final String getUnpairedSequence() {
		return unpairedSequence;
	}
	
	public final String getRead1Sequence() {
		return read1sequence;
	}
	
	public final String getRead2Sequence() {
		return read2sequence;
	}
	
	public final ReadLayout getRead1Layout() {
		return read1layout;
	}
	
	public final ReadLayout getRead2Layout() {
		return read2layout;
	}
	
	public final Annotation getMappedLocation() {
		return location;
	}
	
	/**
	 * Set mapped location of fragment
	 * @param mappedLocation Mapped location
	 */
	public final void setMappedLocation(Annotation mappedLocation) {
		location = mappedLocation;
	}
	
	public final int compareTo(BarcodedFragment other) {
		if(location != null && other.getMappedLocation() != null) {
			int l = location.compareTo(other.getMappedLocation());
			if(l != 0) return l;
		}
		return id.compareTo(other.getId());
	}

	@Override
	public final FragmentGroup getFragmentGroup() {
		return fragmentGroup;
	}

	@Override
	public final void addFragmentWithSameBarcodes(BarcodedFragment fragment) {
		fragmentGroup.addFragment(fragment);
	}

	@Override
	public final String getFullInfoString() {
		return location.getFullInfoString();
	}
	
	/**
	 * Get data accessor for Berkeley DB for this entity type
	 * @param environmentHome Database environment home
	 * @param storeName Database entity store name
	 * @param readOnly Database is read only
	 * @param transactional Database is transactional
	 * @return Data accessor
	 */
	public static final DataAccessor getDataAccessor(String environmentHome, String storeName, boolean readOnly, boolean transactional) {
		logger.info("");
		logger.info("Getting data accessor for database environment " + environmentHome + " and entity store " + storeName + ".");
		return new DataAccessor(environmentHome, storeName, readOnly, transactional);
	}
	
	/*
	 * http://docs.oracle.com/cd/E17277_02/html/GettingStartedGuide/simpleda.html
	 * http://docs.oracle.com/cd/E17277_02/html/GettingStartedGuide/persist_index.html
	 */
	/**
	 * Data accessor which provides access to database
	 * Opens a database environment
	 * Must call close() when finished with this object
	 * @author prussell
	 *
	 */
	public static final class DataAccessor {
		
		PrimaryIndex<String, BasicBarcodedFragment> primaryIndex;
		SecondaryIndex<String, String, BasicBarcodedFragment> secondaryIndexBarcodeString;
		SecondaryIndex<String, String, BasicBarcodedFragment> secondaryIndexReadID;
		private DatabaseEnvironment environment;
		private DatabaseStore entityStore;
		
		/**
		 * Data accessor object. MUST BE CLOSED WHEN DONE.
		 * @param environmentHome Database environment home directory
		 * @param storeName Name of database store
		 * @param readOnly Whether environment should be read only
		 * @param transactional Whether environment should be transactional
		 */
		public DataAccessor(String environmentHome, String storeName, boolean readOnly, boolean transactional) {
		
			/*
			 * http://docs.oracle.com/cd/E17277_02/html/GettingStartedGuide/persist_first.html
			 */
			environment = new DatabaseEnvironment();
			try {
				environment.setup(new File(environmentHome), readOnly, transactional);
			} catch(DatabaseException e) {
				logger.error("Problem with environment setup: " + e.toString());
				System.exit(-1);
			}
			
			entityStore = new DatabaseStore();
			try {
				entityStore.setup(environment.getEnvironment(), storeName, readOnly);
			} catch(DatabaseException e) {
				logger.error("Problem with store setup: " + e.toString());
				System.exit(-1);
			}
			
			primaryIndex = entityStore.getStore().getPrimaryIndex(String.class, BasicBarcodedFragment.class);
			secondaryIndexBarcodeString = entityStore.getStore().getSecondaryIndex(primaryIndex, String.class, "barcodeString");

			// Attach shutdown hook to close
			/*Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					close();
				}
			});*/
			
		}
		
		/**
		 * Set cache size as a percentage of JVM max memory
		 * @param percent Percentage of JVM max memory to allocate to cache
		 */
		public void setCachePercent(int percent) {
			EnvironmentMutableConfig config = environment.getEnvironment().getMutableConfig();
			config.setCachePercentVoid(percent);
			environment.getEnvironment().setMutableConfig(config);
			logger.info("Changed cache size to " + environment.getEnvironment().getMutableConfig().getCacheSize());
		}
		
		/*
		 * http://docs.oracle.com/cd/E17277_02/html/GettingStartedGuide/simpleput.html
		 */
		public void put(Collection<BasicBarcodedFragment> fragments) {
			for(BasicBarcodedFragment fragment : fragments) {
				primaryIndex.put(fragment);
			}
		}
		
		public void put(BasicBarcodedFragment fragment) {
			primaryIndex.putNoReturn(fragment);
		}

		
		/**
		 * Close the store and the environment
		 */
		public void close() {
			logger.info("");
			logger.info("Closing database environment and entity store.");
			entityStore.close();
			environment.close();
		}
		
		/**
		 * Get a cursor over all fragments
		 * Must close when finished
		 * Can either use an iterator over the cursor or treat like a collection
		 * @return Cursor over all fragments
		 */
		public EntityCursor<BasicBarcodedFragment> getAllFragments() {
			return primaryIndex.entities();
		}
		
		
		/**
		 * Get an iterator over all fragments sharing barcodes with fragments mapping to the window
		 * @param barcodedBam Barcoded bam file
		 * @param chr Window chromsome
		 * @param start Window start
		 * @param end Window end
		 * @param contained Fully contained reads only
		 * @return Iterator over all fragments sharing barcodes with fragments mapping to the window, or null if the window contains no mappings
		 * @throws Exception
		 */
		public JoinedEntityCursor<BasicBarcodedFragment> getAllFragmentsWithBarcodesMatchingFragmentInChr(String barcodedBam, String chr) throws Exception {
			return getAllFragmentsWithBarcodesMatchingFragmentInWindow(barcodedBam, chr, 0, Integer.MAX_VALUE, true);
		}
		

		
		/**
		 * Get an iterator over all fragments sharing barcodes with fragments mapping to the window
		 * @param barcodedBam Barcoded bam file
		 * @param chr Window chromsome
		 * @param start Window start
		 * @param end Window end
		 * @param contained Fully contained reads only
		 * @return Iterator over all fragments sharing barcodes with fragments mapping to the window, or null if the window contains no mappings
		 * @throws Exception
		 */
		public JoinedEntityCursor<BasicBarcodedFragment> getAllFragmentsWithBarcodesMatchingFragmentInWindow(String barcodedBam, String chr, int start, int end, boolean contained) throws Exception {
			SAMFileReader reader = new SAMFileReader(new File(barcodedBam));
			SAMRecordIterator iter = reader.query(chr, start, end, contained);
			List<EntityCursor<BasicBarcodedFragment>> cursors = new ArrayList<EntityCursor<BasicBarcodedFragment>>();
			if(!iter.hasNext()) {
				reader.close();
				return null;
			}
			// First put all barcode sequences in a tree set so they are not duplicated
			Collection<String> barcodeSeqs = new TreeSet<String>();
			while(iter.hasNext()) {
				try {
					SAMRecord record = iter.next();
					String b = record.getStringAttribute(BarcodedBamWriter.BARCODES_SAM_TAG);
					barcodeSeqs.add(b);
				} catch (Exception e) {
					for(EntityCursor<BasicBarcodedFragment> c : cursors) {
						c.close();
					}
					reader.close();
					throw e;
				}
			}
			for(String b : barcodeSeqs) {
				try {
					cursors.add(getAllFragmentsWithBarcodes(b));
				} catch (Exception e) {
					for(EntityCursor<BasicBarcodedFragment> c : cursors) {
						c.close();
					}
					reader.close();
					throw e;
				}
			}
			reader.close();
			return new JoinedEntityCursor<BasicBarcodedFragment>(cursors);
		}
		
		/**
		 * Get an iterator over all fragments sharing barcodes with fragments mapping to the region
		 * @param alignmentModel Alignment model loaded with barcoded bam file
		 * @param region Region to query
		 * @param contained Fully contained reads only
		 * @return Iterator over all fragments sharing barcodes with fragments mapping to the region, or null if the region contains no mappings
		 * @throws Exception
		 */
		public JoinedEntityCursor<BasicBarcodedFragment> getAllFragmentsWithBarcodesMatchingFragmentInWindow(AlignmentModel alignmentModel, Annotation region, boolean contained) {
			CloseableIterator<Alignment> iter = alignmentModel.getOverlappingReads(region, contained);
			List<EntityCursor<BasicBarcodedFragment>> cursors = new ArrayList<EntityCursor<BasicBarcodedFragment>>();
			if(!iter.hasNext()) {
				iter.close();
				return null;
			}
			while(iter.hasNext()) {
				try {
					Alignment align = iter.next();
					SAMRecord record = align.toSAMRecord();
					String b = record.getStringAttribute(BarcodedBamWriter.BARCODES_SAM_TAG);
					cursors.add(getAllFragmentsWithBarcodes(b));
				} catch (Exception e) {
					for(EntityCursor<BasicBarcodedFragment> c : cursors) {
						c.close();
					}
					e.printStackTrace();
				}
			}
			iter.close();
			return new JoinedEntityCursor<BasicBarcodedFragment>(cursors);
		}
		
		/**
		 * Get a cursor over all fragments with a particular barcode string
		 * Must close when finished
		 * Can either use an iterator over the cursor or treat like a collection
		 * @param barcodeString Barcode string
		 * @return Cursor over all fragments with this barcode string
		 */
		public EntityCursor<BasicBarcodedFragment> getAllFragmentsWithBarcodes(String barcodeString) {
			return secondaryIndexBarcodeString.subIndex(barcodeString).entities();
		}
		
		/**
		 * Get a cursor over all fragments with a particular ID
		 * Must close when finished
		 * Can either use an iterator over the cursor or treat like a collection
		 * @param id ID
		 * @return Cursor over all fragments with this ID
		 */
		public EntityCursor<BasicBarcodedFragment> getAllFragmentsWithID(String id) {
			return secondaryIndexReadID.subIndex(id).entities();
		}
		
	}
	
}