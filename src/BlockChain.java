import java.util.ArrayList;
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
    	private Map<ByteArrayWrapper, NodeData> nodes;
    	private TimeStamp timeStamp;
    	
    	/**
    	 * A block, UTXOPool after the addition of that block
    	 * and its height.
    	 */
    	private class NodeData {
    	    private final Block block;
    	    private final UTXOPool upSnapshot;
    	    private final int height;
    	    public NodeData(Block block, UTXOPool upSnapshot, int height) {
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
    	    // Enough for this.
    	}
    	
    	/**
    	 * Think of Git's heads.
    	 */
    	private class Head implements Comparable<Head>{
    		private ByteArrayWrapper hash;
    		private int height;
    		private int timeStamp;	// Higher value means newer.
    		/**
    		 * To construct {@code Head} we have to know hash and height of pointed block as well
    		 * as UTXOPool at the point of forking.
    		 * Ctor keeps const-correctness for hash and up, so don't worry.
    		 */
    		public Head(int height, byte[] hash, int timeStamp) {
    			// copy is made inside {@code ByteArrayWrapper} ctor.
    			this(height, new ByteArrayWrapper(hash), timeStamp);
    		}
    		public Head(int height, ByteArrayWrapper hash, int timeStamp) {
    			this.height = height;
    			this.hash = hash;
    			this.timeStamp = timeStamp;
    		}
    		public int getTimeStamp() {
				return timeStamp;
			}
    		private int getHeight() {
				return height;
			}
    		public ByteArrayWrapper getHash() {
    			return hash;
    		}
			public void setTimeStamp(int timeStamp) {
				this.timeStamp = timeStamp;
			}
    		public void incrementHeight() {
    			height++;
    		}
    		public void setHash(ByteArrayWrapper hash) {
    			this.hash = hash;
    		}
    		public void setHash(byte[] hash) {
    			setHash(new ByteArrayWrapper(hash) );
    		}
    		/**
    		 * Heads are equal if hashes are equal.
    		 */
    		@Override
    		public boolean equals(Object obj) {
    			if (this == obj) return true;
    		    if (obj == null) return false;
    		    if (this.getClass() != obj.getClass() ) return false;
    		    Head other = (Head) obj;
    		    return this.getHash().equals(other.getHash() );
    		}
    		@Override
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
    			if(o.getHeight() != this.getHeight() ) {
    				return o.getHeight() - this.getHeight();
    			}
    			return o.getTimeStamp() - this.getTimeStamp();
    		}
			public void setHeight(int height) {
				this.height = height;				
			}
    	}
    	/**
    	 * Ctor
    	 */
    	public Tree(Block genesisBlock) {
    		this.timeStamp = new TimeStamp();
    		initHeads(genesisBlock);
    		initNodes(genesisBlock);
		}

    	private void initNodes(Block genesisBlock) {
    		this.nodes = new HashMap<ByteArrayWrapper, NodeData> ();
    		UTXOPool up = new UTXOPool();	// Genesis pool is *not* empty
    		up.addUTXO(coinbaseUTXO(genesisBlock), coinbaseOutput(genesisBlock) );
    		NodeData genesis = new NodeData(genesisBlock, up, 1);
    		this.nodes.put(blockHash(genesisBlock), genesis);
		}

		private ByteArrayWrapper blockHash(Block block) {
			return new ByteArrayWrapper(block.getHash() );
		}

		private Transaction.Output coinbaseOutput(Block block) {
			return block.getCoinbase().getOutput(0);
		}

		private UTXO coinbaseUTXO(Block block) {
			return new UTXO(block.getCoinbase().getHash(), 0);
		}

		private void initHeads(Block genesisBlock) {
    		this.heads = new ArrayList<Head> ();	// Is LinkedList better?
    		this.heads.add( new Head(1, genesisBlock.getHash(), timeStamp.getStamp() ) );
		}

		private NodeData maxHeight() {
    		// I don't mind linear search considering how small the number of forks is.
    		ByteArrayWrapper hash = Collections.min(heads).getHash();
			return nodes.get(hash);
    	}
    	
		public Block maxHeightBlock() {
			NodeData bp = maxHeight();
			return bp.getBlock();
		}

		public UTXOPool maxHeightUTXOPool() {
			NodeData bp = maxHeight();
			return bp.getUTXOPool();
		}

		/**
		 * ...
		 */
		public boolean addBlockInTree(Block block) {
			/*
			 * Checks come first:
			 */
			if(isGenesis(block) ) return false;
			
			if(tooOld(block) ) return false;
			
			if(wrongParent(block) ) return false;
			
			if(notValid(block) ) return false;
			
			/*
			 * If we arrived this far, we can start
			 * applying some changes to structures.
			 */
			NodeData newNode = createNewNode(block);
			nodes.put(blockHash(block), newNode);
			
			updateHeads(blockHash(block) );
			
			reduceNodes();
			
			reduceHeads();	// Not necessary
			
			return true;
		}

		private void reduceHeads() {
			// TODO Auto-generated method stub
			
		}

		/*
		 * Remove old nodes.
		 * Note that this is not optimisation, but necessary for
		 * the program to function as described.
		 */
		private void reduceNodes() {
			List<ByteArrayWrapper> dropList = new LinkedList<ByteArrayWrapper> ();
			for(ByteArrayWrapper hash : nodes.keySet() ) {
				if(nodeTooOld(hash) )
					dropList.add(hash);
			}
			for(ByteArrayWrapper hash : dropList ) {
				nodes.remove(hash);
			}
		}

		private boolean nodeTooOld(ByteArrayWrapper hash) {
			int height = nodes.get(hash).getHeight();
			int maxHeight = maxHeight().getHeight();
			return !(height + 1 > (maxHeight - CUT_OFF_AGE));
		}

		private void updateHeads(ByteArrayWrapper hash) {
			ByteArrayWrapper prevHash = new ByteArrayWrapper(
					nodes.get(hash).getBlock().getPrevBlockHash() 
					);
			int headIdx = indexInHeads(prevHash);
			if(headIdx == -1) {	// Add
				Head head = new Head(
						nodes.get(hash).getHeight(), 
						hash, 
						timeStamp.getStamp() 
						);
				heads.add(head);
			} else {	// Update
				heads.get(headIdx).setHash(hash);
				heads.get(headIdx).setTimeStamp(timeStamp.getStamp() );
				heads.get(headIdx).setHeight(nodes.get(hash).getHeight() );
			}
			
		}

		private int indexInHeads(ByteArrayWrapper hash) {
			for(int idx = 0; idx < heads.size(); idx++) {
				Head temp = new Head(11, hash, 111);
				if(temp.equals(heads.get(idx) ) )	// heads.get(idx).getHash().equals(hash) might have been better..
					return idx;
			}
			return -1;
		}

		private NodeData createNewNode(Block block) {
			UTXOPool newUp = reducedUtxo(block);
			addCoinbase(newUp, block);
			ByteArrayWrapper parentHash = new ByteArrayWrapper(block.getPrevBlockHash() );
			int height = nodes.get(parentHash).getHeight() + 1;
			NodeData newNode = new NodeData(block, newUp, height);
			return newNode;
		}

		private void addCoinbase(UTXOPool newUp, Block block) {
			newUp.addUTXO(coinbaseUTXO(block), coinbaseOutput(block) );
		}

		private UTXOPool reducedUtxo(Block block) {
			ByteArrayWrapper parentHash = new ByteArrayWrapper(block.getPrevBlockHash() );
			NodeData parentData = nodes.get(parentHash);
			TxHandler txHandler = new TxHandler(parentData.getUTXOPool() );
			List<Transaction> txList = block.getTransactions();
			Transaction[] txs = txList.toArray(new Transaction[txList.size() ] );
			txHandler.handleTxs(txs);
			return txHandler.getUTXOPool();	// Return updated utxoPool
		}

		private boolean isGenesis(Block block) {
			return block.getPrevBlockHash() == null;
		}

		private boolean notValid(Block block) {
			// Assume not null:
			ByteArrayWrapper parentHash = new ByteArrayWrapper(block.getPrevBlockHash() );
			// Assume exists:
			NodeData parentData = nodes.get(parentHash);
			UTXOPool up = parentData.getUTXOPool();
			TxHandler txHandler = new TxHandler(up);
			List<Transaction> txList = block.getTransactions();
			Transaction[] txs = txList.toArray(new Transaction[txList.size() ] );
			Transaction[] handled = txHandler.handleTxs(txs);
			/*
			 * Now this is a weak link.
			 * It won't let bad blocks pass, but it might stop good
			 * blocks from being published.
			 * TODO
			 */
			if(txList.size() != handled.length) return true;
			return false;
		}

		private boolean wrongParent(Block block) {
			return !nodes.containsKey(new ByteArrayWrapper(block.getPrevBlockHash() ) );
		}

		private boolean tooOld(Block block) {
			return wrongParent(block);
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
		/*
		 * Is this even needed?
		 * I just have to store txs, it is not my responsibility that they make
		 * sense.
		 * What if I just ditch all txs on fork change?
		 */
	}

	/** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        txPool.addTransaction(tx);	// I think it's that simple.
    }
}