import java.nio.ByteBuffer;
import java.security.PublicKey;
import java.util.*;

public class TxHandler {

	/**
	 * This class functions as a struct.
	 * Contains transaction and hashes of transactions that depend directly
	 * on its outputs.
	 */
	private class TxExt {

		private Transaction tx;
		private ByteBuffer hash;
		private Set<ByteBuffer> dep;

		/**
		 * @param tx - {@link Transaction} object.
		 */
		public TxExt(Transaction tx) {
			this.tx = tx;
			dep = new HashSet<ByteBuffer> ();
			hash = ByteBuffer.wrap(tx.getHash() );
			// last line might not be ideal for testing but who cares.
		}

		/**
		 * It is convenient to have hash of transaction as an object so that
		 * one can use java.util structures. For that purpose {@link ByteBuffer} is an ideal
		 * wrapper.
		 * 
		 * @return hash as {@link ByteBuffer} object.
		 */
		public ByteBuffer getHash() {
			return hash;
		}

		/**
		 * Instead of wrapping commonly used functions let me return whole
		 * transaction instead.
		 * 
		 * @return tx - {@link Transaction} object.
		 */
		public Transaction getTx() {
			return tx;
		}

		/**
		 * Self-explanatory.
		 * 
		 * @param hash - {@link ByteBuffer} object.
		 */
		public void addDependentTx(ByteBuffer hash) {
			dep.add(hash);
		}

		/**
		 * Set of dependent transactions. Copied to make sure it is not
		 * messed with.
		 * 
		 * @return dep - {@link Set} object.
		 */
		public Set<ByteBuffer> getDependents() {
			return new HashSet<ByteBuffer> (dep);
		}

	}

	UTXOPool utxoPool;

	/**
	 * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
	 * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
	 * constructor.
	 */
	public TxHandler(UTXOPool utxoPool) {
		this.utxoPool = new UTXOPool(utxoPool);
	}

	/*
	 * generalize isValidTx(Transaxtion tx) by adding custom pool into
	 * arguments list.
	 */
	private boolean isValidTx(Transaction tx, UTXOPool hypothPool) {
		// no particular order is necessary
		return (
				claimedOutputsValid(tx.getInputs(), hypothPool ) &&
				inputSignaturesValid(tx, hypothPool) &&
				doubleSpendingAbsent(tx.getInputs() ) &&
				outputsArePositive(tx.getOutputs() ) &&	// there are no credits in ScroogeCoin
				valueConserved(tx.getInputs(), tx.getOutputs(), hypothPool )
				);
	}

	/**
	 * @return true if:
	 * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
	 * (2) the signatures on each input of {@code tx} are valid, 
	 * (3) no UTXO is claimed multiple times by {@code tx},
	 * (4) all of {@code tx}s output values are non-negative, and
	 * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
	 *     values; and false otherwise.
	 */
	public boolean isValidTx(Transaction tx) {
		return isValidTx(tx, utxoPool);
	}


	// easy
	private boolean claimedOutputsValid(ArrayList<Transaction.Input> inputs, UTXOPool hypothPool) {
		for(Transaction.Input input : inputs) {
			UTXO inputUtxo = new UTXO(input.prevTxHash, input.outputIndex);
			if(!hypothPool.contains(inputUtxo) ) return false;
		}
		return true;
	}

	// easy
	private boolean inputSignaturesValid(Transaction tx, UTXOPool hypothPool) {
		for(int idx = 0; idx < tx.numInputs(); idx++) {
			Transaction.Input input = tx.getInput(idx);
			byte[] inputSignature = input.signature;
			byte[] inputSignedMessage = tx.getRawDataToSign(idx);
			Transaction.Output claimedOutput = getClaimedOutput(input, hypothPool);	// could be null
			PublicKey address = null;
			if(claimedOutput != null)
				address = claimedOutput.address;
			/*
			 * NOTE: i hope verifySignature(...) method has correct behavior when null
			 * address is passed and returns false. otherwise that case
			 * must be handled above in if else clause.
			 * 
			 * looks like that is exactly the case.
			 */
			if(!Crypto.verifySignature(address, inputSignedMessage, inputSignature) ) return false;
		}
		return true;
	}

	// util
	private Transaction.Output getClaimedOutput(Transaction.Input input, UTXOPool hypothPool) {
		UTXO inputUtxo = new UTXO(input.prevTxHash, input.outputIndex);
		Transaction.Output claimedOutput = hypothPool.getTxOutput(inputUtxo);
		return claimedOutput;
	}

	// easy, no assumptions
	private boolean doubleSpendingAbsent(ArrayList<Transaction.Input> inputs) {
		/*
		 *  put claimed utxos in set and see if they 'fit'.
		 */
		Set<UTXO> claimedUtxos = new HashSet<UTXO> ();
		for(Transaction.Input input : inputs) {
			UTXO claimedUtxo = new UTXO(input.prevTxHash, input.outputIndex);
			if(!claimedUtxos.add(claimedUtxo) ) return false;
		}
		return true;
	}

	// super-easy
	private boolean outputsArePositive(ArrayList<Transaction.Output> outputs) {
		for(Transaction.Output output : outputs)
			if(output.value < 0)
				return false;
		return true;
	}

	// easy
	private boolean valueConserved(ArrayList<Transaction.Input> inputs, ArrayList<Transaction.Output> outputs, UTXOPool hypothPool) {
		double totalInputValue = totalInputValue(inputs, hypothPool);
		double totalOutputValue = totalOutputValue(outputs);
		return totalInputValue >= totalOutputValue;
	}

	// util, super-easy
	private double totalOutputValue(ArrayList<Transaction.Output> outputs) {
		double totalOutputValue = 0;
		for(Transaction.Output output : outputs)
			totalOutputValue += output.value;
		return totalOutputValue;
	}

	/*
	 * util
	 */
	private double totalInputValue(ArrayList<Transaction.Input> inputs, UTXOPool hypothPool) {
		double totalInputValue = 0;
		for(Transaction.Input input : inputs) {
			Transaction.Output claimedOutput = getClaimedOutput(input, hypothPool);
			if(claimedOutput != null)
				totalInputValue += claimedOutput.value;
		}
		return totalInputValue;
	}

	// ////////////////////////////////////////////////////////////////////////////// isValid(...) end

	/**
	 * Handles each epoch by receiving an unordered array of proposed transactions, checking each
	 * transaction for correctness, returning a mutually valid array of accepted transactions, and
	 * updating the current UTXO pool as appropriate.
	 */
	public Transaction[] handleTxs(Transaction[] possibleTxs) {
		/*
		 * create mapping from hash to transaction and its dependent transactions.
		 */
		Map<ByteBuffer, TxExt> hash2TxExt = hash2TxExtMap(possibleTxs);
		/*
		 * first of all, self-inconsistent transactions and those depending on it 
		 * have to be removed from hash2TxExt map.
		 * (not considering double spending)
		 */
		removeSelfInconsistentTxs(hash2TxExt);
		/*
		 * now we can deal with double spending.
		 */
		cleanFromDoubleSpending(hash2TxExt);
		/*
		 * take care of utxoPool
		 */
		updateUtxoPool(hash2TxExt);
		return convertToArrayOfTxs(hash2TxExt);
		
	}

	/*
	 * required return type of handleTxs(...) method is an array. this converts mapping from
	 * hash to TxExt to array of Transactions.
	 */
	private Transaction[] convertToArrayOfTxs(Map<ByteBuffer, TxExt> hash2TxExt) {
		Transaction[] validTxs = new Transaction[hash2TxExt.size() ];
		int idx = 0;
		for(TxExt txExt : hash2TxExt.values() ) {
			validTxs[idx++] = txExt.getTx();
		}
		return validTxs;
	}

	/*
	 * this method puts all outputs as legit utxos in the pool and
	 * then consumes what is necessary for given set of txs.
	 * whats left is updated pool.
	 */
	private void updateUtxoPool(Map<ByteBuffer, TxExt> hash2TxExt) {
		// put in 
		for(TxExt txExt : hash2TxExt.values() ) {
			putOutputsInThePool(txExt.getTx() );
		}

		// consume utxos
		for(TxExt txExt : hash2TxExt.values() ) {
			ArrayList<Transaction.Input> inputs = txExt.getTx().getInputs();
			consumeUtxos(inputs);
		}

	}

	// go through all inputs and remove used utxos
	private void consumeUtxos(ArrayList<Transaction.Input> inputs) {
		for(Transaction.Input input : inputs) {
			UTXO consumedUtxo = new UTXO(input.prevTxHash, input.outputIndex);
			utxoPool.removeUTXO(consumedUtxo);
		}

	}

	// put outputs of given tx into the pool
	private void putOutputsInThePool(Transaction tx) {
		for(int idx = 0; idx < tx.numOutputs(); idx++) {
			// this utxo might not be final since some other transaction in block might consume it, hence temporary
			UTXO tempUtxo = new UTXO(tx.getHash(), idx);
			Transaction.Output tempOutput = tx.getOutput(idx);
			utxoPool.addUTXO(tempUtxo, tempOutput);
		}

	}

	/*
	 * take care of double spending.
	 */
	private void cleanFromDoubleSpending(Map<ByteBuffer, TxExt> hash2TxExt) {
		Map<UTXO, Set<ByteBuffer> > spenders = utxo2SpendersMap(hash2TxExt);
		// hopefully editing elements does not mess up iterator.
		for(Set<ByteBuffer> doubleSpenders : spenders.values() ) {
			updateDoubleSpenders(doubleSpenders, hash2TxExt);
			dealWithDoubleSpend(hash2TxExt, doubleSpenders);
		}

	}

	/*
	 * this method removes txs so that given set does not contain more than two txs.
	 */
	private void dealWithDoubleSpend(Map<ByteBuffer, TxExt> hash2TxExt, Set<ByteBuffer> doubleSpenders) {
		/*
		 * if tx2 depends on tx1 and spends the same utxo tx2 has to go
		 * without any second thoughts.
		 * fix: this should update doubleSpenders accordingly.
		 */
		removeConflictingDependentTxs(hash2TxExt, doubleSpenders);
		/*
		 * once that is done, it is safe to remove txs until doubleSpenders set
		 * size goes below 2.
		 */
		while(doubleSpenders.size() > 1) {
			// pick any double spender
			ByteBuffer hash = pickOneElement(doubleSpenders);
			// and remove from hash2TxExt as well as from doubleSpenders
			/*
			 * we know at this point that each removal decreases doubleSpenders size
			 * by 1. therefore we dont have to chck if some other elements are to be removed.
			 */
			doubleSpenders.remove(hash);
			removeWithDependents(hash, hash2TxExt);
			
		}

	}

	private void removeConflictingDependentTxs(Map<ByteBuffer, TxExt> hash2TxExt, Set<ByteBuffer> doubleSpenders) {
		// for each tx spending the same output
		for(ByteBuffer hash : doubleSpenders) {
			/*
			 *  collect dependent txs including indirectly dependent ones.
			 *  not including itself.
			 */
			Set<ByteBuffer> depDeep = collectDependentTxs(hash, hash2TxExt);
			// remove those dependent txs
			for(ByteBuffer depHash : depDeep) {
				/*
				 * fixed: i did not check if depHash was also included doubleSpenders.
				 * we want to remove txs which are dependent AND spending the same output.
				 */
				if(doubleSpenders.contains(depHash) ) {
					removeWithDependents(depHash, hash2TxExt);
					
				}
				
			}
			
		}
		
		updateDoubleSpenders(doubleSpenders, hash2TxExt);

	}

	// some txs were removed and that has to show on doubleSpenders set
	private void updateDoubleSpenders(Set<ByteBuffer> doubleSpenders, Map<ByteBuffer, TxExt> hash2TxExt) {
		// first make a copy so that iterator does not make mistakes when doing operations on doubleSpenders
		Set<ByteBuffer> doubleSpendersCopy = new HashSet<ByteBuffer> (doubleSpenders);
		for(ByteBuffer hash : doubleSpendersCopy) {
			if(!hash2TxExt.containsKey(hash) ) doubleSpenders.remove(hash);
		}
	}

	private Set<ByteBuffer> collectDependentTxs(ByteBuffer hash, Map<ByteBuffer, TxExt> hash2TxExt) {
		Set<ByteBuffer> depDeep = new HashSet<ByteBuffer> ();
		// take tx
		TxExt txExt = hash2TxExt.get(hash);
		// if it is already removed (null) return empty set
		if(txExt == null) return depDeep;
		/*
		 * not to include itself, we start collecting dependent
		 * txs from its children.
		 */
		Set<ByteBuffer> dirDeps = txExt.getDependents();
		for(ByteBuffer dirDep : dirDeps) {
			addDependentTxs(dirDep, depDeep, hash2TxExt);
		}
		return depDeep;

	}

	// recursive
	private void addDependentTxs(ByteBuffer dirDep, Set<ByteBuffer> depDeep, Map<ByteBuffer, TxExt> hash2TxExt) {
		TxExt txExt = hash2TxExt.get(dirDep);
		/* 
		 * base case is when it is already added to the set, or for some reason it
		 * is alrady removed (is null).
		 */
		if(txExt == null || depDeep.contains(dirDep) ) return;
		// like in removing recursion, first add
		depDeep.add(dirDep);
		// then call recursively next steps
		for(ByteBuffer nextHash : txExt.getDependents() ) {
			addDependentTxs(nextHash, depDeep, hash2TxExt);
		}

	}

	// util
	private ByteBuffer pickOneElement(Set<ByteBuffer> doubleSpenders) {
		ByteBuffer hash = null;
		Iterator<ByteBuffer> it = doubleSpenders.iterator();
		if(it.hasNext() )
			hash = it.next();
		return hash;
		
	}

	/*
	 * this method returns mapping from utxo to set of txs using that utxo.
	 */
	private Map<UTXO, Set<ByteBuffer> > utxo2SpendersMap(Map<ByteBuffer, TxExt> hash2TxExt) {
		// map to populate
		Map<UTXO, Set<ByteBuffer> > spenders = new HashMap<UTXO, Set<ByteBuffer> > ();
		// for each tx
		for(TxExt txExt : hash2TxExt.values() ) {
			ByteBuffer hash = txExt.getHash();
			Transaction tx = txExt.getTx();
			// take all inputs
			for(Transaction.Input input : tx.getInputs() ) {
				// and see which utxo it consumes
				UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
				/*
				 * if this utxo is not already included in spenders map add it as a key.
				 * then add tx as its consumer.
				 */
				if(!spenders.containsKey(utxo) ) spenders.put(utxo, new HashSet<ByteBuffer> () );
				spenders.get(utxo).add(hash);
			}
		}
		return spenders;
		
	}

	/*
	 * transaction is self consistent if signatures are valid, outputs are positive and all that jazz,
	 * but it is allowed to consume outputs from other transactions of its block.
	 * double spending is not considered.
	 */
	private void removeSelfInconsistentTxs(Map<ByteBuffer, TxExt> hash2TxExt) {
		UTXOPool hypothPool = createHypothUtxoPool(hash2TxExt);
		Set<ByteBuffer> badTxs = findSelfInconsistentTxs(hash2TxExt, hypothPool);
		for(ByteBuffer hash : badTxs) {
			removeWithDependents(hash, hash2TxExt);
		}
		
	}

	/*
	 * just go through each transaction and see if it is valid on given pool.
	 * record them and return.
	 */
	private Set<ByteBuffer> findSelfInconsistentTxs(Map<ByteBuffer, TxExt> hash2TxExt, UTXOPool hypothPool) {
		Set<ByteBuffer> badTxs = new HashSet<ByteBuffer> ();
		for(TxExt txExt : hash2TxExt.values() ) {
			Transaction tx = txExt.getTx();
			if(!isValidTx(tx, hypothPool) ) badTxs.add(txExt.getHash() );
		}
		return badTxs;
		
	}

	/*
	 * this method will recursively remove tx and txs that depend on it.
	 * it does not pose a problem if some txs on the way were already removed by
	 * this method.
	 */
	private void removeWithDependents(ByteBuffer hash, Map<ByteBuffer, TxExt> hash2TxExt) {
		// base case: if already not present
		if(!hash2TxExt.containsKey(hash) ) return;
		// if present, save hashes of its dependent txs and remove it.
		Set<ByteBuffer> dep = hash2TxExt.get(hash).getDependents();
		/*
		 * remove it first. note that it is necessary to do this now 
		 * to avoid infinite cycle.
		 */
		hash2TxExt.remove(hash);
		// call recursively
		for(ByteBuffer nextHash : dep) {
			removeWithDependents(nextHash, hash2TxExt);
		}

	}

	/*
	 * this method creates utxo pool which contains all current utxos as 
	 * well as outputs from these transactions.
	 */
	private UTXOPool createHypothUtxoPool(Map<ByteBuffer, TxExt> hash2TxExt) {
		UTXOPool hypothPool = new UTXOPool(utxoPool);
		for(TxExt txExt : hash2TxExt.values() ) {
			Transaction tx = txExt.getTx();
			putOutputsInUtxoPool(hypothPool, tx);
		}
		return hypothPool;
		
	}

	// util
	private void putOutputsInUtxoPool(UTXOPool hypothPool, Transaction tx) {
		for(int idx = 0; idx < tx.numOutputs(); idx++) {
			Transaction.Output output = tx.getOutput(idx);
			UTXO utxo = new UTXO(tx.getHash(), idx);
			hypothPool.addUTXO(utxo, output);
		}

	}

	/*
	 * this method populates hash2TxExt map.
	 */
	private Map<ByteBuffer, TxExt> hash2TxExtMap(Transaction[] possibleTxs) {
		Map<ByteBuffer, TxExt> hash2TxExt = new HashMap<ByteBuffer, TxExt> ();
		putTxsInMap(possibleTxs, hash2TxExt);
		putDepInMap(hash2TxExt);
		return hash2TxExt;

	}

	private void putDepInMap(Map<ByteBuffer, TxExt> hash2TxExt) {
		// for each transaction
		for(TxExt txExt : hash2TxExt.values() ) {
			Transaction tx = txExt.getTx();
			// see its inputs
			for(Transaction.Input input : tx.getInputs() ) {
				ByteBuffer hash = ByteBuffer.wrap(input.prevTxHash);
				/*
				 *  add this tx as dependent to transaction outputting its claimed utxo.
				 *  this was buggy before because i didnt pay attention if prevTxHash was present 
				 *  in block of transactions.
				 *  added checking part.
				 */
				if(hash2TxExt.containsKey(hash) ) {
					hash2TxExt.get(hash).addDependentTx(txExt.getHash() );
				}
				
			}
			
		}
		
	}

	private void putTxsInMap(Transaction[] possibleTxs, Map<ByteBuffer, TxExt> hash2TxExt) {
		for(Transaction tx : possibleTxs) {
			TxExt txExt = new TxExt(tx);
			hash2TxExt.put(txExt.getHash(), txExt);
		}

	}

}
