import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
    	    private final int height;
    	    public BlockAndPool(Block block, UTXOPool upSnapshot, int height) {
    	    	this.height = height;
    	    	this.block = block;
    	    	this.upSnapshot = upSnapshot;
			}
    	    public Block getBlock() {
				return block;
			}
    	    public UTXOPool getUTXOPool() {
				return upSnapshot;
			}
    	    public int getHeight() {
    	    	return height;
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
    		public void setHash(ByteArrayWrapper hash) {
    			this.hash = hash;
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
    		UTXOPool newUP = new UTXOPool();	// Genesis pool is *not* empty
    		newUP.addUTXO(new UTXO(genesisBlock.getCoinbase().getHash(), 0), genesisBlock.getCoinbase().getOutput(0) );
    		BlockAndPool genesis = new BlockAndPool(genesisBlock, newUP, 1);
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

		/**
		 * (i) passed block must not be too old
		 * (ii) passed block must have valid tx set (This is actually 
		 * already taken care of by BlockHandler, I won't check it twice).
		 */
		public boolean addBlockInTree(Block block) {
			
			// New genesis, not again..
	    	if(block.getPrevBlockHash() == null) {
	    		return false;
	    	}
	    	
			/*
			 *  Make sure tree contains a parent, which is necessary condition.
			 *  This is also equivalent to checking height condition, because I'm removing
			 *  old parents from tree on each addBlock. 
			 */
	    	ByteArrayWrapper prevHash = new ByteArrayWrapper(block.getPrevBlockHash() );
			if(!blocks.keySet().contains(prevHash) ) return false;
			int newHeight = blocks.get(prevHash).getHeight() + 1;
			
			/*
			 *  Now make sure block is valid by looking at its transactions.
			 */
			TxHandler txHandler = new TxHandler(blocks.get(prevHash).getUTXOPool() );
			List<Transaction> txs = block.getTransactions();
			Transaction[] left = txs.toArray(new Transaction[txs.size() ] );
			// If any transaction is missing, block is bad:
			if(left.length != block.getTransactions().size() ) return false;
			
			// This will be used as a new utxo pool.
			UTXOPool up = txHandler.getUTXOPool();
			// Add coinbase to it:
			Transaction coinbase = blocks.get(prevHash).getBlock().getCoinbase();
			up.addUTXO(new UTXO(coinbase.getHash(), 0), coinbase.getOutput(0) );
			// Put new block in map:
			BlockAndPool bp = new BlockAndPool(block, up, newHeight);
			blocks.put(new ByteArrayWrapper(block.getHash() ), bp);
			
			// Now take care of the heads.
			/*
			 * First, see if heads contained prevHash block.
			 */
			int headIdx = -1;
			for(int i = 0; i < heads.size(); i++) {
				if(heads.get(i).getHash().equals(prevHash) ) {
					headIdx = i;
					break;
				}
			}
			/*
			 * Do some changes to heads.
			 */
			ByteArrayWrapper hash = new ByteArrayWrapper(block.getHash() );
			if(headIdx != -1) {	// Edit existing head.
				heads.get(headIdx).setHash(hash);
				heads.get(headIdx).incrementHeight();
				heads.get(headIdx).setTimeStamp(timeStamp.getStamp() );
			} else {	// Add new Head.
				Head newHead = new Head(newHeight, block.getHash(), timeStamp.getStamp() );
				heads.add(newHead);
			}
			
			/*
			 * We have to drop old blocks.
			 */
			List<ByteArrayWrapper> dropList = new LinkedList<ByteArrayWrapper>();
			for(ByteArrayWrapper gash : blocks.keySet() ) {
				// Age condition:
				if(blocks.get(gash).getHeight() + 1 <= maxHeight().getHeight() - CUT_OFF_AGE) {
					dropList.add(gash);
				}
			}
			for(ByteArrayWrapper gash : dropList) {
				blocks.remove(gash);
			}
			
			return true;
		}
    	
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
    	
    	// If tree is unable to accept that block, return no.
    	if(!tree.addBlockInTree(block) ) return false;

    	// If tree accepted it, we can remove block transactions from txPool.
    	for(Transaction tx : block.getTransactions() ) {
    		txPool.removeTransaction(tx.getHash() );
    	}
    	
    	reorganizeOutstandingPool();
    	
		return true;
    }

    /*
     * This method should update {@code txPool} if a new
     * branch became taller.
     * Some arguments might be added, this is just a stub. 
     */
    private void reorganizeOutstandingPool() {
		// TODO Auto-generated method stub
		
	}

	/** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        txPool.addTransaction(tx);	// I think it's that simple.
    }
}