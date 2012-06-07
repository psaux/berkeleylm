package edu.berkeley.nlp.lm.values;

import java.util.List;

import edu.berkeley.nlp.lm.array.CustomWidthArray;
import edu.berkeley.nlp.lm.array.LongArray;
import edu.berkeley.nlp.lm.bits.BitList;
import edu.berkeley.nlp.lm.bits.BitStream;
import edu.berkeley.nlp.lm.collections.Indexer;
import edu.berkeley.nlp.lm.collections.LongToIntHashMap;
import edu.berkeley.nlp.lm.collections.LongToIntHashMap.Entry;
import edu.berkeley.nlp.lm.util.Logger;
import edu.berkeley.nlp.lm.util.LongRef;
import edu.berkeley.nlp.lm.util.Annotations.OutputParameter;
import edu.berkeley.nlp.lm.util.Annotations.PrintMemoryCount;

public final class ProbBackoffValueContainer extends RankedValueContainer<ProbBackoffPair>
{

	private static final long serialVersionUID = 964277160049236607L;

	private transient LongToIntHashMap hasBackoffValIndexer;

	private static final int HAS_BACKOFF = 1;

	private static final int NO_BACKOFF = 0;

	@PrintMemoryCount
	float[] backoffsForRank;

	@PrintMemoryCount
	final CustomWidthArray probsAndBackoffsForRank; // ugly, but we but probs and backoffs consecutively in this area to improve cache locality

	@PrintMemoryCount
	final float[] probsForRank; // ugly, but we but probs and backoffs consecutively in this area to improve cache locality

	private int backoffWidth = -1;

	public ProbBackoffValueContainer(final LongToIntHashMap countCounter, final int valueRadix, final boolean storePrefixes, int maxNgramOrder) {
		super(valueRadix, storePrefixes, maxNgramOrder);
		Logger.startTrack("Storing values");
		final boolean hasDefaultVal = countCounter.get(getDefaultVal().asLong(), -1) >= 0;
		//		hasBackoffValIndexer = new LongToIntHashMap();
		//		noBackoffValIndexer = new LongToIntHashMap();
		List<Entry> objectsSortedByValue = countCounter.getObjectsSortedByValue(true);
		Indexer<Float> probIndexer = new Indexer<Float>();
		Indexer<Float> backoffIndexer = new Indexer<Float>();
		probIndexer.getIndex(ProbBackoffPair.probOf(getDefaultVal().asLong()));
		backoffIndexer.getIndex(ProbBackoffPair.backoffOf(getDefaultVal().asLong()));
		hasBackoffValIndexer = new LongToIntHashMap();
		for (Entry e : objectsSortedByValue) {
			final float backoff = ProbBackoffPair.backoffOf(e.key);
			final float prob = ProbBackoffPair.probOf(e.key);
			probIndexer.getIndex(prob);
			backoffIndexer.getIndex(backoff);
		}
		probsForRank = new float[probIndexer.size()];
		int a = 0;
		for (float f : probIndexer.getObjects()) {
			probsForRank[a++] = f;
		}
		backoffsForRank = new float[backoffIndexer.size()];
		int b = 0;
		for (float f : backoffIndexer.getObjects()) {
			backoffsForRank[b++] = f;
		}
		backoffWidth = CustomWidthArray.numBitsNeeded(backoffIndexer.size());
		final int width = CustomWidthArray.numBitsNeeded(probIndexer.size()) + backoffWidth;
		probsAndBackoffsForRank = new CustomWidthArray(objectsSortedByValue.size() + (hasDefaultVal ? 0 : 1), width);
		probsAndBackoffsForRank.ensureCapacity(objectsSortedByValue.size() + (hasDefaultVal ? 0 : 1));
		for (Entry e : objectsSortedByValue) {

			final float backoff = ProbBackoffPair.backoffOf(e.key);
			final float prob = ProbBackoffPair.probOf(e.key);
			int probIndex = probIndexer.getIndex(prob);
			int backoffIndex = backoffIndexer.getIndex(backoff);
			long together = combine(probIndex, backoffIndex);
			hasBackoffValIndexer.put(e.key, (int) probsAndBackoffsForRank.size());
			probsAndBackoffsForRank.addWithFixedCapacity(together);

			if (probsAndBackoffsForRank.size() == defaultValRank && !hasDefaultVal) {
				addDefault(probIndexer, backoffIndexer);

			}
			//			if (backoff == 0.0f) {
			//				noBackoffValIndexer.put(e.key, noBackoffValIndexer.size());
			//			} else {
			//				hasBackoffValIndexer.put(e.key, hasBackoffValIndexer.size());
			//			}
		}
		if (probsAndBackoffsForRank.size() < defaultValRank && !hasDefaultVal) {
			addDefault(probIndexer, backoffIndexer);

		}

		wordWidth = CustomWidthArray.numBitsNeeded(probsAndBackoffsForRank.size());
		//		for (java.util.Map.Entry<Long, Integer> entry : hasBackoffValIndexer.entries()) {
		//			probsAndBackoffsForRank[entry.getValue()] = entry.getKey();
		//		}
		//		for (java.util.Map.Entry<Long, Integer> entry : noBackoffValIndexer.entries()) {
		//			probsForRank[(entry.getValue())] = ProbBackoffPair.probOf(entry.getKey());
		//		}

		Logger.logss("Storing count indices using " + wordWidth + " bits.");
		Logger.endTrack();
	}

	/**
	 * @param probIndexer
	 * @param backoffIndexer
	 * @param backoffWidth
	 * @param k
	 * @return
	 */
	private void addDefault(Indexer<Float> probIndexer, Indexer<Float> backoffIndexer) {
		final float dbackoff = ProbBackoffPair.backoffOf(getDefaultVal().asLong());
		final float dprob = ProbBackoffPair.probOf(getDefaultVal().asLong());
		int dprobIndex = probIndexer.getIndex(dprob);
		int dbackoffIndex = backoffIndexer.getIndex(dbackoff);
		long dtogether = combine(dprobIndex, dbackoffIndex);
		hasBackoffValIndexer.put(getDefaultVal().asLong(), (int) probsAndBackoffsForRank.size());
		probsAndBackoffsForRank.addWithFixedCapacity(dtogether);
	}

	/**
	 * @param dprobIndex
	 * @param dbackoffIndex
	 * @return
	 */
	private long combine(int dprobIndex, int dbackoffIndex) {
		return (((long) dprobIndex) << backoffWidth) | dbackoffIndex;
	}

	private int backoffRankOf(long val) {
		return (int) (val & ((1L << backoffWidth) - 1));
	}

	private int probRankOf(long val) {
		return (int) (val >>> backoffWidth);
	}

	/**
	 * @param valueRadix
	 * @param storePrefixIndexes
	 * @param maxNgramOrder
	 * @param hasBackoffValIndexer
	 * @param noBackoffValIndexer
	 * @param probsAndBackoffsForRank
	 * @param probsForRank
	 * @param hasBackoffValIndexer
	 */
	public ProbBackoffValueContainer(int valueRadix, boolean storePrefixIndexes, int maxNgramOrder, float[] probsForRank, float[] backoffsForRank,
		CustomWidthArray probsAndBackoffsForRank, int wordWidth, LongToIntHashMap hasBackoffValIndexer, int backoffWidth) {
		super(valueRadix, storePrefixIndexes, maxNgramOrder);
		this.backoffsForRank = backoffsForRank;
		this.probsAndBackoffsForRank = probsAndBackoffsForRank;
		this.probsForRank = probsForRank;
		super.wordWidth = wordWidth;
		this.hasBackoffValIndexer = hasBackoffValIndexer;
		this.backoffWidth = backoffWidth;
	}

	@Override
	public ProbBackoffValueContainer createFreshValues() {
		return new ProbBackoffValueContainer(valueRadix, storeSuffixIndexes, valueRanks.length, probsForRank, backoffsForRank, probsAndBackoffsForRank,
			wordWidth, hasBackoffValIndexer,backoffWidth);
	}

	public final float getProb(final int ngramOrder, final long index) {
		return getCount(ngramOrder, index, false);
	}

	public final long getInternalVal(final int ngramOrder, final long index) {
		return valueRanks[ngramOrder].get(index);
	}

	public final float getProb(final CustomWidthArray valueRanksForOrder, final long index) {
		return getCount(valueRanksForOrder, index, false);
	}

	@Override
	public void getFromOffset(final long index, final int ngramOrder, @OutputParameter final ProbBackoffPair outputVal) {
		final int rank = getRank(ngramOrder, index);
		getFromRank(rank, outputVal);
	}

	/**
	 * @param ngramOrder
	 * @param index
	 * @param uncompressProbs2
	 * @return
	 */
	private float getCount(final int ngramOrder, final long index, final boolean backoff) {
		final int rank = getRank(ngramOrder, index);
		return getFromRank(rank, backoff);
	}

	private float getCount(final CustomWidthArray valueRanksForOrder, final long index, final boolean backoff) {
		final int rank = getRank(valueRanksForOrder, index);
		return getFromRank(rank, backoff);
	}

	private float getFromRank(final int rank, final boolean backoff) {
		long val = probsAndBackoffsForRank.get(rank);
		return backoff ? backoffsForRank[backoffRankOf(val)] : probsForRank[probRankOf(val)];

		//		if (rank % 2 == HAS_BACKOFF)
		//			return backoff ? ProbBackoffPair.backoffOf(probsAndBackoffsForRank[rank >> 1]) : ProbBackoffPair.probOf(probsAndBackoffsForRank[rank >> 1]);
		//		else
		//			return backoff ? 0.0f : probsForRank[rank >> 1];
	}

	public final float getBackoff(final int ngramOrder, final long index) {
		return getCount(ngramOrder, index, true);
	}

	public final float getBackoff(final CustomWidthArray valueRanksForNgramOrder, final long index) {
		return getCount(valueRanksForNgramOrder, index, true);
	}

	@Override
	protected ProbBackoffPair getDefaultVal() {
		return new ProbBackoffPair(Float.NaN, Float.NaN);
	}

	@Override
	protected void getFromRank(final int rank, @OutputParameter final ProbBackoffPair outputVal) {

		outputVal.prob = getFromRank(rank, false);
		outputVal.backoff = getFromRank(rank, true);
	}

	@Override
	public ProbBackoffPair getScratchValue() {
		return new ProbBackoffPair(Float.NaN, Float.NaN);
	}

	@Override
	public void setFromOtherValues(final ValueContainer<ProbBackoffPair> o) {
		super.setFromOtherValues(o);
		this.hasBackoffValIndexer = ((ProbBackoffValueContainer) o).hasBackoffValIndexer;
	}

	@Override
	public void trim() {
		super.trim();
		hasBackoffValIndexer = null;
	}

	@Override
	protected int getCountRank(long val) {
		return hasBackoffValIndexer.get(val, -1);
	}
}