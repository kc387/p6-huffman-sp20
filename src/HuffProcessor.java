import java.util.*;
/**
 * Spring 2020
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int counts[] = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);

		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);

		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
	}
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){

		int magic = in.readBits(BITS_PER_INT);
		if (magic != HUFF_TREE) {
			throw new HuffException("invalid magic number "+magic);
		}
		HuffNode root = readTree(in);
		HuffNode current = root;
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else {
				if (bits == 0) current = current.myLeft;
				else current = current.myRight;
				if (current.myLeft == null && current.myRight == null) {
					if (current.myValue == PSEUDO_EOF)
						break;   // out of loop
					else {
						out.writeBits(BITS_PER_WORD, current.myValue); ;
						current = root; // start back after leaf
					}
				}
			}
		}
		out.close();
	}

	public HuffNode readTree(BitInputStream in){
		int bit = in.readBits(1);
		if (bit == -1) throw new HuffException("reading bits failed");
		if (bit == 0){
			HuffNode left = readTree(in);
			HuffNode right = readTree(in);
			return new HuffNode(0,0,left,right);
		}
		else {
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value,0,null,null);
		}
	}

	public int[] readForCounts(BitInputStream in){
		int[] freq = new int[ALPH_SIZE + 1];
		freq[PSEUDO_EOF] = 1;
		while(true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			freq[val] += 1;
		}
		return freq;
	}

	public HuffNode makeTreeFromCounts(int[] freq){
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for(int index = 0; index < freq.length; index++) {
			if(freq[index] > 0) pq.add(new HuffNode(index,freq[index],null,null));
		}
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			// create new HuffNode t with weight from
			// left.weight+right.weight and left, right subtrees
			HuffNode t = new HuffNode(0, left.myWeight + right.myWeight, left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}

	public String[] makeCodingsFromTree(HuffNode root){
		String[] encodings = new String[ALPH_SIZE + 1];
		paths(root, "", encodings);
		return encodings;
	}

	public void paths(HuffNode root, String path, String[] encodings){
		if (root.myLeft == null && root.myRight == null){
			encodings[root.myValue] = path;
			return;
		}
		paths(root.myLeft, path + "0", encodings);
		paths(root.myRight, path + "1", encodings);
	}

	public void writeHeader(HuffNode root, BitOutputStream out){
		if(root == null) return;
		if(root.myLeft != null || root.myRight != null){
			out.writeBits(1, 0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
		if(root.myLeft == null && root.myRight == null){
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD+1, root.myValue);
		}
	}

	public void writeCompressedBits(String[] encodings, BitInputStream in, BitOutputStream out){
		while(true){
			int val = in.readBits(BITS_PER_WORD);
			if(val == -1) break;
			String code = encodings[val];
			out.writeBits(code.length(), Integer.parseInt(code,  2));
		}
		String code = encodings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code,2));
	}
}