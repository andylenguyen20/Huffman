import java.io.IOException;
import java.util.PriorityQueue;

/**
 * Interface that all compression suites must implement. That is they must be
 * able to compress a file and also reverse/decompress that process.
 * 
 * @author Brian Lavallee
 * @since 5 November 2015
 * @author Owen Atrachan
 * @since December 1, 2016
 */
public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); // or 256
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE = HUFF_NUMBER | 1;
	public static final int HUFF_COUNTS = HUFF_NUMBER | 2;
	public static final int HUFF_SPECIAL = HUFF_NUMBER | 3;

	public enum Header {
		TREE_HEADER, COUNT_HEADER
	};

	public Header myHeader = Header.TREE_HEADER;

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out) {
		int id = in.readBits(BITS_PER_INT);
		if (id != HUFF_NUMBER && id != HUFF_TREE && id!= HUFF_SPECIAL) {
			throw new HuffException("Bad input! File not compressed!");
		}
		HuffNode root = readTreeHeader(in);
		readCompressedBits(in, out, root);
		/*
		 * DEFAULTED while (true){ int val = in.readBits(BITS_PER_WORD); if (val
		 * == -1) break;
		 * 
		 * out.writeBits(BITS_PER_WORD, val); }
		 */
	}

	public void setHeader(Header header) {
		myHeader = header;
		System.out.println("header set to " + myHeader);
	}

	// Helper methods for decompress!!!
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit == 1) {
			HuffNode hn = new HuffNode(in.readBits(9), 1);
			return hn;
		}
		HuffNode hn1 = new HuffNode(0, 0, readTreeHeader(in), readTreeHeader(in));
		return hn1;
	}

	private void readCompressedBits(BitInputStream in, BitOutputStream out, HuffNode root) {
		HuffNode curr = root;
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("Bad input! No PSUEDO_EOF");
			}
			if (bits == 0) { // if 0 --> left
				curr = curr.left();
			} else {
				curr = curr.right(); // if 1 --> right
			}
			if (curr.left() == null && curr.right() == null) {
				if (curr.value() == PSEUDO_EOF) {
					break;
				}
				out.writeBits(BITS_PER_WORD, curr.value());
				curr = root;
			}

		}
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out) {
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		// out.writeBits(BITS_PER_INT, HUFF_NUMBER);
		writeHeader(root, out);

		in.reset();

		writeCompressedBits(in, codings, out);

		// checking final and initial sizes to see compression
		int initSize = in.bitsRead();
		int finalSize = out.bitsWritten();

		// didn't compress
		if (initSize <= finalSize) {
			throw new HuffException("Compressed file is larger!");
		}

		/*
		 * DEFAULT while (true){ int val = in.readBits(BITS_PER_WORD); if (val
		 * == -1) break;
		 * 
		 * out.writeBits(BITS_PER_WORD, val); }
		 */
	}

	// Helper methods for compress!!!

	/*
	 * Write readForCounts that reads a BitInputStream parameter and returns an
	 * int[] array of 256 int values such that ret[val] is the number of
	 * occurrences of val in the BitInputStream file that was read, where val is
	 * an int between 0 and 255. Conveniently 256 is 28 and 8 is BITS_PER_WORD,
	 * so you can read the BitInputStream parameter using readBits but reading
	 * BITS_PER_WORD bits at a time and return an int[] of count values as
	 * described.
	 */
	private int[] readForCounts(BitInputStream in) {
		int[] ret = new int[256];
		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) {
				break;
			}
			ret[val]++;
		}
		return ret;
	}

	private HuffNode makeTreeFromCounts(int[] arr) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for (int i = 0; i < arr.length; i++) {
			if (arr[i] == 0) {
				continue;
			}
			pq.add(new HuffNode(i, arr[i]));
		}
		pq.add(new HuffNode(PSEUDO_EOF, 1));
		// call pq.add(new HuffNode(...)) for every 8-bit value that
		// occur one or more times, including PSEUDO_EOF!!!

		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(-1, left.weight() + right.weight(), left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}

	private String[] makeCodingsFromTree(HuffNode curr) {
		String[] encodings = new String[257];
		return makeCodingsFromTree(curr, "", encodings);
	}

	private String[] makeCodingsFromTree(HuffNode curr, String path, String[] encodings) {
		if (curr.left() == null && curr.right() == null) {
			encodings[curr.value()] = path;
		} else {
			makeCodingsFromTree(curr.left(), path + "0", encodings);
			makeCodingsFromTree(curr.right(), path + "1", encodings);
		}
		return encodings;
	}

	private void writeHeader(HuffNode root, BitOutputStream out) {
		if (out.bitsWritten() == 0) {
			// out.writeBits(BITS_PER_INT, HUFF_NUMBER);
			out.writeBits(BITS_PER_INT, HUFF_SPECIAL);
			//out.writeBits(BITS_PER_INT, HUFF_TREE);
		}

		if (root.left() == null && root.right() == null) {
			out.writeBits(1, 1);
			out.writeBits(9, root.value());
		} else {
			out.writeBits(1, 0);
			if(root.left() != null){
				writeHeader(root.left(), out);
			}
			if(root.right() != null){
				writeHeader(root.right(), out);
			}
		}
	}

	private void writeCompressedBits(BitInputStream in, String[] encodings, BitOutputStream out) {
		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1)
				break;
			String encoding = encodings[val];
			out.writeBits(encoding.length(), Integer.parseInt(encoding, 2));
		}
		String eof = encodings[256];
		out.writeBits(eof.length(), Integer.parseInt(eof, 2));
	}

}