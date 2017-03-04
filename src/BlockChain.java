import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

public class BlockChain {
	
	// private vars
	private List<Head> heads;
	private Map<ByteArrayWrapper, Pair> blocks;
	private TransactionPool txPool;
	private int counter;	// Block counter
	// Just inc block counter whenever new block arrives, successful or not.
	// That way we can differentiate order of blocks.
	
	// public vars
    public static final int CUT_OFF_AGE = 10;
	
	/*
	 * Here's the idea.
	 * The structure is directed acyclic graph (DAG).
	 * Directed part in DAG is obvious.
	 * The fact that it is acyclic follows from append-only character of blockchain.
	 * But not only is it a DAG, it is a tree as well, which follows from the fact that blocks have only one parent.
	 * 
	 * To store that tree I'll use Git like mechanism (although Git is just a DAG).
	 * We'll store references to leafs in a list {@code heads}.
	 * Ideally, {@code heads} will contain only one reference, but there is a possibility
	 * of several references being stored when there are forks in blockchain.
	 * {@code heads} list will always be sorted according to block height/age comparison, so
	 * that it would be safe to pick 0th element whenever leading fork is needed.
	 * Head will contain hash and height of pointed block, as well as
	 * TxHandler (from which, UTXOPool) for that fork.
	 * 
	 * Blocks will be stored in a map where keys are their hashes.
	 * I'll keep blocks in memory only less than {@code CUT_OFF_AGE} deep from each head.
	 *
	 * To make age comparison work, I'll use Pair<Block, Integer> as values in
	 * {@code blocks} map.
	 * That enables us to include receiving order and later compare blocks by age.
	 */
	
	/**
	 * This class acts similar to Git's head but for blocks instead of commits.
	 * {@code hashCode} and {@code equals} are provided by pointed blocks hashcode, which means
	 * {@code heads} set can only contain one fork per block.
	 * Sounds reasonable.
	 * @author aro
	 */
	private class Head implements Comparable<Head>{
		// The hash of block this head is pointing to.
		private ByteArrayWrapper hash;
		// Height of pointed block.
		private int height;
		/*
		 *  Each fork has its own TxHandler, which will help validating block transactions as well as keep
		 *  current UTXOPool.
		 */
		private TxHandler txHandler;
		/**
		 * To construct {@code Head} we have to know hash and height of pointed block as well
		 * as UTXOPool at the point of forking.
		 * Ctor keeps const-correctness for hash and up, so don't worry.
		 */
		public Head(int height, byte[] hash, UTXOPool up) {
			this.height = height;
			// TxHandler makes a copy of UTXOPool for itself.
			this.txHandler = new TxHandler(up);
			// copy is made inside {@code ByteArrayWrapper} ctor.
			this.hash = new ByteArrayWrapper(hash);
		}
		/**
		 * This is only needed accessor to fork height.
		 */
		public void incrementHeight() {
			height++;
		}
		/**
		 * Getter for head height.
		 */
		public int getHeight() {
			return height;
		}
		/**
		 * Simply give out UTXOPool for convenience. 
		 */
		public UTXOPool getUTXOPool() {
			return txHandler.getUTXOPool();
		}
		/**
		 * Getter for head block hash.
		 * @return
		 */
		public ByteArrayWrapper getHash() {
			return hash;
		}
		/**
		 * Overriding equals.
		 */
		public boolean equals(Object other) {
			return this.hash.equals(other);
		}
		/**
		 * Overriding hashCode.
		 */
		public int hashCode() {
			return this.hash.hashCode();
		}
		/**
		 * As described, comparison is based on height or age
		 * whenever necessary.
		 */
		@Override
		public int compareTo(Head o) {
			// Subtraction order is reversed since we want higher/older blocks to come first.
			if(o.height != this.height) {
				return o.height - this.height;
			}
			return blocks.get(o.getHash() ).getNum() - blocks.get(this.getHash() ).getNum(); 
		}
	}

	/**
	 * Helper class to enable storing a pair as blocks map values.
	 * Comparison and hashing rely only on block object.
	 * Lifted from StackOverflow.
	 */
	private class Pair {
	    private final Block block;
	    private final int num;
	    public Pair(Block block, int num) {
	    	this.block = block;
	    	this.num = num;
	    }
	    public Block getBlock() {
	    	return block;
	    }
	    public int getNum() {
	    	return num;
	    }
	    @Override
	    public boolean equals(Object o) {
	    	if(o == null) return false;
	        if (!(o instanceof Pair)) {
	            return false;
	        }
	        Pair p = (Pair) o;
	        // Comparing digest is enough:
	        return Arrays.equals(this.getBlock().getHash(), p.getBlock().getHash() );
	    }
	    @Override
	    public int hashCode() {
	        return this.getBlock().hashCode();
	    }
	}
	
	
	
	
	
	
	
	

	

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
    	// heads
        this.heads = new ArrayList<Head>();	// Might choose LinkedList afterwards..
        Head master = new Head(/* genesis height */ 1, genesisBlock.getHash(), genesisUTXOPool(genesisBlock) );
        this.heads.add(master);
        // blocks
        this.blocks = new HashMap<ByteArrayWrapper, Pair>();
        this.blocks.put(new ByteArrayWrapper(genesisBlock.getHash() ), new Pair(genesisBlock, 0) );
        // tx pool
        this.txPool = new TransactionPool();
        // counter
        this.counter = 1;	// zero was genesis block
    }
    
    private UTXOPool genesisUTXOPool(Block genesisBlock) {
    	/*
    	 * Consensus says that conbase transaction can be spent only in the
    	 * next block.
    	 * Hence, genesis coinbase is not added to the pool right now and genesis pool will be
    	 * empty.
    	 * Genesis tx has to be pure coinbase without any usual txs.
    	 * I won't check that as we're not writing industrial degree application.
    	 */
		return new UTXOPool();
	}

	/** Get the maximum height block */
    public Block getMaxHeightBlock() {
    	/*
    	 * Heads list will always be sorted so that highest
    	 * head will be first element.
    	 * Note that heads list will always have at least one element.
    	 */
    	ByteArrayWrapper hash = heads.get(0).getHash();
    	Pair p = blocks.get(hash);
		return p.getBlock();
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
		return heads.get(0).getUTXOPool();
    }
    
    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
		return txPool;
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * 
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <=
     * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
     * at height 2.
     * 
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
		return false;
        // IMPLEMENT THIS
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        /*
         *  It is not our responsibility to check if tx is ok,
         *  Just add it:
         */
    	txPool.addTransaction(tx);
    }
}