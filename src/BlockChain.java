import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.HandshakeCompletedEvent;

// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

public class BlockChain {
	
	// private vars
	private Set<Head> heads;
	private Map<ByteArrayWrapper, Block> blocks;
	private TransactionPool txPool;
	
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
	 * We'll store references to leafs in a set {@code heads}.
	 * Ideally, {@code heads} will contain only one reference, but there is a possibility
	 * of several references being stored when there are forks in blockchain.
	 * For convenience, reference will contain hash and height of pointed block, as well as
	 * UTXOPool for that fork.
	 * 
	 * Blocks will be stored in a map where keys are their hashes.
	 * I'll keep blocks in memory only less than {@code CUT_OFF_AGE} deep from each head.
	 * 
	 * ...
	 */
	
	/**
	 * This class acts similar to Git's head but for blocks instead of commits.
	 * {@code hashCode} and {@code equals} are provided by pointed blocks hashcode, which means
	 * {@code heads} set can only contain one fork per block.
	 * Sounds reasonable.
	 * @author aro
	 */
	private class Head {
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
	}
	
	
	
	
	
	
	
	


    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
    	// heads
        this.heads = new HashSet<Head>();
        Head master = new Head(1, genesisBlock.getHash(), genesisUTXOPool(genesisBlock) );
        this.heads.add(master);
        // blocks
        this.blocks = new HashMap<ByteArrayWrapper, Block>();
        this.blocks.put(new ByteArrayWrapper(genesisBlock.getHash() ), genesisBlock);
        // tx pool
        this.txPool = new TransactionPool();
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
		return null;
        // IMPLEMENT THIS
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
		return null;
        // IMPLEMENT THIS
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
		return null;
        // IMPLEMENT THIS
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
        // IMPLEMENT THIS
    }
}