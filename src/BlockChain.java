import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

public class BlockChain {
	
	// private vars
	private TransactionPool txPool;
	private Tree tree;
	
	// public vars
    public static final int CUT_OFF_AGE = 10;
	
    /**
     * Simple helper class to timestamp objects.
     */
    private class TimeStamp {
    	private int counter;
    	public TimeStamp() {
    		counter = 0;
    	}
    	// Returns ever-increasing number on each call.
    	public int getStamp() {
			return counter++;
    		
    	}
    }
    
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
	 * Head will contain hash and height of pointed block, as well as
	 * TxHandler (from which, UTXOPool) for that fork.
	 * Additionally, {@code Head} will contain integer that timestamps them, higher value
	 * meaning head was updated more recently.
	 * 
	 * Blocks will be stored in a map where keys are their hashes.
	 * I'll keep blocks in memory only less than {@code CUT_OFF_AGE} deep from each head.
	 * For convenience, blocks will be stored with corresponding TxHandlers so that
	 * we don't have to manually construct UTXOPool each time new fork is made.
	 *
	 */
    
    /**
     * Blockchain tree abstraction.
     */
    private class Tree {
    	private List<Head> heads;
    	private Map<ByteArrayWrapper, BlockAndPool> blocks;
    	private TimeStamp timeStamp;
    	
    	/**
    	 * A block and its TxHandler.
    	 */
    	private class BlockAndPool {
    	    private final Block block;
    	    private final UTXOPool upSnapshot;
    	    public BlockAndPool(Block block, UTXOPool txHandler) {
    	    	this.block = block;
    	    	this.upSnapshot = txHandler;
			}
    	    public Block getBlock() {
				return block;
			}
    	    public UTXOPool getUTXOPool() {
				return upSnapshot;
			}
			@Override
    	    public boolean equals(Object o) {
    	    	if(o == null) return false;
    	        if (!(o instanceof BlockAndPool)) {
    	            return false;
    	        }
    	        BlockAndPool p = (BlockAndPool) o;
    	        // Comparing digest is enough:
    	        return Arrays.equals(this.getBlock().getHash(), p.getBlock().getHash() );
    	    }
    	    @Override
    	    public int hashCode() {
    	        return this.getBlock().hashCode();
    	    }
    	}
    	
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
    		 *  Higher value means newer.
    		 *  Actually, timestamp would be a nice application of
    		 *  static variables, but unfortunately to upload this assignment I
    		 *  need to keep things in a single file and use inner classes,
    		 *  which don't allow static variables.
    		 */
    		private int timeStamp;
    		/**
    		 * To construct {@code Head} we have to know hash and height of pointed block as well
    		 * as UTXOPool at the point of forking.
    		 * Ctor keeps const-correctness for hash and up, so don't worry.
    		 */
    		public Head(int height, byte[] hash, int timeStamp) {
    			this.height = height;
    			// copy is made inside {@code ByteArrayWrapper} ctor.
    			this.hash = new ByteArrayWrapper(hash);
    			this.timeStamp = timeStamp;
    		}
    		/**
    		 * @return int timestamp of this head.
    		 */
    		public int getTimeStamp() {
				return timeStamp;
			}
    		/**
    		 * Update timestamp value. 
    		 */
			public void setTimeStamp(int timeStamp) {
				this.timeStamp = timeStamp;
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
    		 * Getter for head block hash.
    		 * @return
    		 */
    		public ByteArrayWrapper getHash() {
    			return hash;
    		}
    		/**
    		 * Overriding equals.
    		 * Heads are equal if hashes are equal.
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
    			return o.getTimeStamp() - this.getTimeStamp();
    		}
    	}

    	public Tree(Block genesisBlock) {
    		this.timeStamp = new TimeStamp();
    		this.heads = new ArrayList<Head>();	// Is LinkedList better?
    		this.heads.add(new Head(1, genesisBlock.getHash(), timeStamp.getStamp() ) );
    		this.blocks = new HashMap<ByteArrayWrapper, BlockAndPool>();
    		UTXOPool newUP = new UTXOPool();	// Genesis pool is empty
    		BlockAndPool genesis = new BlockAndPool(genesisBlock, newUP );
    		this.blocks.put(new ByteArrayWrapper(genesisBlock.getHash()), genesis);
		}

    	private BlockAndPool maxHeight() {
    		// I don't mind linear search considering how small the number of forks is.
    		ByteArrayWrapper hash = Collections.min(heads).getHash();
			return blocks.get(hash);
    	}
    	
		public Block maxHeightBlock() {
			BlockAndPool bp = maxHeight();
			return bp.getBlock();
		}

		public UTXOPool maxHeightUTXOPool() {
			BlockAndPool bp = maxHeight();
			return bp.getUTXOPool();
		}
    	
    	// TODO
    	
    }
	


	
	
	
	
	
	
	
	

	

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
    	this.tree = new Tree(genesisBlock);
    	this.txPool = new TransactionPool();	// Empty tx pool.
    }
    
	/** Get the maximum height block */
    public Block getMaxHeightBlock() {
    	return tree.maxHeightBlock();
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
		return tree.maxHeightUTXOPool();
    }
    
    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
		return txPool;	// I think it's that simple
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
        txPool.addTransaction(tx);	// I think it's that simple.
    }
}