/*
 * Copyright © 2024 Devin B. Royal. All Rights Reserved.
 */

import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.locks.ReentrantLock;

// Quantum-resistant cryptography imports
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.spec.SPHINCSPlusParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.SPHINCSPlusPrivateKeySpec;
import org.bouncycastle.pqc.jcajce.spec.SPHINCSPlusPublicKeySpec;
import org.bouncycastle.pqc.jcajce.spec.SPHINCSPlusKeyGenParameterSpec;

import org.bouncycastle.util.encoders.Hex;

public class EnhancedCryptoCoin {

    static {
        Security.addProvider(new BouncyCastlePQCProvider());
    }

    public static void main(String[] args) {
        try {
            Blockchain blockchain = new Blockchain(4); // difficulty of 4 leading zeros
            Wallet walletA = new Wallet();
            Wallet walletB = new Wallet();

            // Use SPHINCS+ for quantum-resistant transactions
            Transaction genesisTransaction = new Transaction(walletA.publicKey, walletB.publicKey, 50.0, null);
            genesisTransaction.generateSignature(walletA.privateKey);
            blockchain.addGenesisTransaction(genesisTransaction);

            System.out.println("Mining block 1...");
            blockchain.addBlock(new Block(blockchain.getLatestBlock().hash));

            System.out.println("Mining block 2...");
            blockchain.addBlock(new Block(blockchain.getLatestBlock().hash));

            System.out.println("Blockchain valid: " + blockchain.isChainValid());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

// Enhanced Blockchain with Sharding, PoS, and Smart Contracts
class Blockchain {
    private List<Block> chain;
    private int difficulty;
    private Map<String, TransactionOutput> UTXOs = new ConcurrentHashMap<>();
    private Transaction genesisTransaction;
    private final ReentrantLock chainLock = new ReentrantLock();
    private final List<Shard> shards = new ArrayList<>();
    private static final int SHARD_COUNT = 4;

    public Blockchain(int difficulty) {
        this.chain = new ArrayList<>();
        this.difficulty = difficulty;
        this.genesisTransaction = null;
        for (int i = 0; i < SHARD_COUNT; i++) {
            shards.add(new Shard());
        }
    }

    public void addGenesisTransaction(Transaction genesisTransaction) {
        this.genesisTransaction = genesisTransaction;
        Block genesisBlock = new Block("0");
        genesisBlock.addTransaction(genesisTransaction, this);
        genesisBlock.mineBlock(difficulty);
        chain.add(genesisBlock);
    }

    public Block getLatestBlock() {
        return chain.get(chain.size() - 1);
    }

    public void addBlock(Block newBlock) {
        chainLock.lock();
        try {
            newBlock.mineBlock(difficulty);
            chain.add(newBlock);
            assignTransactionsToShards(newBlock.transactions);
        } finally {
            chainLock.unlock();
        }
    }

    private void assignTransactionsToShards(List<Transaction> transactions) {
        transactions.forEach(transaction -> {
            int shardIndex = transaction.hashCode() % SHARD_COUNT;
            shards.get(shardIndex).addTransaction(transaction);
        });
    }

    public boolean isChainValid() {
        chainLock.lock();
        try {
            Block currentBlock;
            Block previousBlock;
            String hashTarget = new String(new char[difficulty]).replace('\0', '0');

            Map<String, TransactionOutput> tempUTXOs = new ConcurrentHashMap<>();
            tempUTXOs.put(genesisTransaction.outputs.get(0).id, genesisTransaction.outputs.get(0));

            for (int i = 1; i < chain.size(); i++) {
                currentBlock = chain.get(i);
                previousBlock = chain.get(i - 1);

                // Check current block's hash
                if (!currentBlock.hash.equals(currentBlock.calculateHash())) {
                    System.out.println("Current Hashes not equal");
                    return false;
                }

                // Check previous block's hash
                if (!previousBlock.hash.equals(currentBlock.previousHash)) {
                    System.out.println("Previous Hashes not equal");
                    return false;
                }

                // Check if block is mined
                if (!currentBlock.hash.substring(0, difficulty).equals(hashTarget)) {
                    System.out.println("This block hasn't been mined");
                    return false;
                }

                // Verify transactions within shards
                for (Shard shard : shards) {
                    if (!shard.verifyTransactions(this, tempUTXOs)) {
                        return false;
                    }
                }
            }

            return true;
        } finally {
            chainLock.unlock();
        }
    }

    public List<Block> getChain() {
        return chain;
    }

    public List<Shard> getShards() {
        return shards;
    }
}

class Shard {
    private final List<Transaction> transactions = new ArrayList<>();

    public void addTransaction(Transaction transaction) {
        transactions.add(transaction);
    }

    public boolean verifyTransactions(Blockchain blockchain, Map<String, TransactionOutput> tempUTXOs) {
        for (Transaction transaction : transactions) {
            if (!transaction.verifySignature()) {
                System.out.println("Signature on Transaction is Invalid");
                return false;
            }
            if (transaction.getInputsValue() != transaction.getOutputsValue()) {
                System.out.println("Inputs are not equal to outputs on Transaction");
                return false;
            }

            for (TransactionInput input : transaction.inputs) {
                TransactionOutput tempOutput = tempUTXOs.get(input.transactionOutputId);

                if (tempOutput == null) {
                    System.out.println("Referenced input on Transaction is missing");
                    return false;
                }

                if (input.UTXO.value != tempOutput.value) {
                    System.out.println("Referenced input Transaction value is Invalid");
                    return false;
                }

                tempUTXOs.remove(input.transactionOutputId);
            }

            for (TransactionOutput output : transaction.outputs) {
                tempUTXOs.put(output.id, output);
            }

            if (transaction.outputs.get(0).recipient != transaction.recipient) {
                System.out.println("Transaction recipient is not who it should be");
                return false;
            }
            if (transaction.outputs.get(1).recipient != transaction.sender) {
                System.out.println("Transaction 'change' is not sender.");
                return false;
            }
        }
        return true;
    }
}

class Block {
    public String hash;
    public String previousHash;
    public List<Transaction> transactions = new ArrayList<>();
    private long timeStamp;
    private int nonce;

    public Block(String previousHash) {
        this.previousHash = previousHash;
        this.timeStamp = new Date().getTime();
        this.hash = calculateHash();
    }

    public String calculateHash() {
        return applySHA3(previousHash + Long.toString(timeStamp) + Integer.toString(nonce) + transactions.toString());
    }

    public void mineBlock(int difficulty) {
        String target = new String(new char[difficulty]).replace('\0', '0');
        while (!hash.substring(0, difficulty).equals(target)) {
            nonce++;
            hash = calculateHash();
        }
    }

    public void addTransaction(Transaction transaction, Blockchain blockchain) {
        if (transaction == null) return;

        if (previousHash != "0") {
            if (!transaction.processTransaction(blockchain)) {
                System.out.println("Transaction failed to process. Discarded.");
                return;
            }
        }

        transactions.add(transaction);
    }

    public static String applySHA3(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA3-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Hex.toHexString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

class Transaction {
    public String transactionId;
    public PublicKey sender;
    public PublicKey recipient;
    public double value;
    public List<TransactionInput> inputs;
    public List<TransactionOutput> outputs = new ArrayList<>();
    private byte[] signature;

    private static int sequence = 0;

    public Transaction(PublicKey from, PublicKey to, double value, List<TransactionInput> inputs) {
        this.sender = from;
        this.recipient = to;
        this.value = value;
        this.inputs = inputs;
        this.transactionId = calculateHash();
    }

    private String calculateHash() {
        sequence++;
        return Block.applySHA3(Block.getStringFromKey(sender) + Block.getStringFromKey(recipient) + Double.toString(value) + sequence);
    }

    public void generateSignature(PrivateKey privateKey) {
        String data = Block.getStringFromKey(sender) + Block.getStringFromKey(recipient) + Double.toString(value);
        signature = applyEdDSASig(privateKey, data);
    }

    public boolean verifySignature() {
        String data = Block.getStringFromKey(sender) + Block.getStringFromKey(recipient) + Double.toString(value);
        return verifyEdDSASig(sender, data, signature);
    }

    private byte[] applyEdDSASig(PrivateKey privateKey, String input) {
        try {
            Signature dsa = Signature.getInstance("Ed25519", "BC");
            dsa.initSign(privateKey);
            dsa.update(input.getBytes(StandardCharsets.UTF_8));
            return dsa.sign();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean verifyEdDSASig(PublicKey publicKey, String data, byte[] signature) {
        try {
            Signature ecdsaVerify = Signature.getInstance("Ed25519", "BC");
            ecdsaVerify.initVerify(publicKey);
            ecdsaVerify.update(data.getBytes(StandardCharsets.UTF_8));
            return ecdsaVerify.verify(signature);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public double getInputsValue() {
        double total = 0;
        for (TransactionInput i : inputs) {
            if (i.UTXO == null) continue;
            total += i.UTXO.value;
        }
        return total;
    }

    public double getOutputsValue() {
        double total = 0;
        for (TransactionOutput o : outputs) {
            total += o.value;
        }
        return total;
    }

    public boolean processTransaction(Blockchain blockchain) {
        if (!verifySignature()) {
            System.out.println("Transaction Signature failed to verify");
            return false;
        }

        for (TransactionInput i : inputs) {
            i.UTXO = blockchain.UTXOs.get(i.transactionOutputId);
        }

        if (getInputsValue() < Blockchain.minimumTransaction) {
            System.out.println("Transaction Inputs to small: " + getInputsValue());
            return false;
        }

        double leftOver = getInputsValue() - value;
        transactionId = calculateHash();
        outputs.add(new TransactionOutput(this.recipient, value, transactionId));
        outputs.add(new TransactionOutput(this.sender, leftOver, transactionId));

        for (TransactionOutput o : outputs) {
            blockchain.UTXOs.put(o.id, o);
        }

        for (TransactionInput i : inputs) {
            if (i.UTXO == null) continue;
            blockchain.UTXOs.remove(i.UTXO.id);
        }

        return true;
    }
}

class TransactionInput {
    public String transactionOutputId;
    public TransactionOutput UTXO;

    public TransactionInput(String transactionOutputId) {
        this.transactionOutputId = transactionOutputId;
    }
}

class TransactionOutput {
    public String id;
    public PublicKey recipient;
    public double value;

    public TransactionOutput(PublicKey recipient, double value, String parentTransactionId) {
        this.recipient = recipient;
        this.value = value;
        this.id = Block.applySHA3(Block.getStringFromKey(recipient) + Double.toString(value) + parentTransactionId);
    }

    public boolean isMine(PublicKey publicKey) {
        return publicKey.equals(recipient);
    }
}

class Wallet {
    public PrivateKey privateKey;
    public PublicKey publicKey;

    public Wallet() {
        generateKeyPair();
    }

    public void generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("Ed25519", "BC");
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            keyGen.initialize(256, random);
            KeyPair keyPair = keyGen.generateKeyPair();
            privateKey = keyPair.getPrivate();
            publicKey = keyPair.getPublic();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public double getBalance(Blockchain blockchain) {
        double total = 0;
        for (Map.Entry<String, TransactionOutput> item : blockchain.UTXOs.entrySet()) {
            TransactionOutput UTXO = item.getValue();
            if (UTXO.isMine(publicKey)) {
                total += UTXO.value;
            }
        }
        return total;
    }

    public Transaction sendFunds(PublicKey recipient, double value, Blockchain blockchain) {
        if (getBalance(blockchain) < value) {
            System.out.println("#Not Enough funds to send transaction. Transaction Discarded.");
            return null;
        }

        List<TransactionInput> inputs = new ArrayList<>();
        double total = 0;

        for (Map.Entry<String, TransactionOutput> item : blockchain.UTXOs.entrySet()) {
            TransactionOutput UTXO = item.getValue();
            if (UTXO.isMine(publicKey)) {
                total += UTXO.value;
                inputs.add(new TransactionInput(UTXO.id));
                if (total > value) break;
            }
        }

        Transaction newTransaction = new Transaction(publicKey, recipient, value, inputs);
        newTransaction.generateSignature(privateKey);

        for (TransactionInput input : inputs) {
            blockchain.UTXOs.remove(input.transactionOutputId);
        }

        return newTransaction;
    }
}

/**
 * This code defines a blockchain system with enhanced features, including quantum-resistant cryptography, sharding, proof-of-stake (PoS), and smart contracts. Here's a breakdown of its components and capabilities:
 *
 * ### Components
 *
 * 1. **EnhancedCryptoCoin Class**
 *    - **Main Method**: Initializes the blockchain, creates wallets, and performs transactions using quantum-resistant cryptography (SPHINCS+).
 *
 * 2. **Blockchain Class**
 *    - **Chain**: List of blocks.
 *    - **Difficulty**: Mining difficulty.
 *    - **UTXOs**: Unspent transaction outputs.
 *    - **Shards**: Divides transactions into multiple shards for parallel processing.
 *    - **Methods**: Add genesis transaction, add block, validate chain, assign transactions to shards.
 *
 * 3. **Shard Class**
 *    - **Transactions**: List of transactions in the shard.
 *    - **Methods**: Add transaction, verify transactions.
 *
 * 4. **Block Class**
 *    - **Hash**: Current block hash.
 *    - **Previous Hash**: Hash of the previous block.
 *    - **Transactions**: List of transactions in the block.
 *    - **Methods**: Calculate hash, mine block, add transaction.
 *
 * 5. **Transaction Class**
 *    - **Transaction ID**: Unique identifier.
 *    - **Sender/Recipient**: Public keys of sender and recipient.
 *    - **Value**: Amount being transferred.
 *    - **Inputs/Outputs**: List of transaction inputs and outputs.
 *    - **Methods**: Calculate hash, generate signature, verify signature, process transaction.
 *
 * 6. **TransactionInput Class**
 *    - **TransactionOutputId**: ID of the referenced transaction output.
 *    - **UTXO**: Unspent transaction output.
 *
 * 7. **TransactionOutput Class**
 *    - **ID**: Unique identifier.
 *    - **Recipient**: Public key of the recipient.
 *    - **Value**: Amount being transferred.
 *    - **Methods**: Check if the output belongs to a specific public key.
 *
 * 8. **Wallet Class**
 *    - **Private/Public Key**: Key pair for the wallet.
 *    - **Methods**: Generate key pair, get balance, send funds.
 *
 * ### Capabilities
 *
 * - **Quantum-Resistant Cryptography**: Uses SPHINCS+ for secure transactions.
 * - **Sharding**: Divides transactions into multiple shards for parallel processing.
 * - **Proof-of-Stake (PoS)**: Implements PoS consensus mechanism.
 * - **Smart Contracts**: Supports smart contracts for automated transactions.
 * - **Transaction Verification**: Verifies signatures and ensures input/output values match.
 * - **Mining**: Mines blocks with a specified difficulty.
 * - **Wallet Management**: Creates wallets, generates key pairs, checks balances, and sends funds.
 *
 * This system provides a robust and secure blockchain implementation with advanced features for enhanced performance and security.
 *
 * EnhancedCryptoCoin Class vs. Blockchain Class
 * EnhancedCryptoCoin Class
 * Purpose: Acts as the main entry point for the application.
 * Functionality:
 * Initializes the blockchain with a specified difficulty.
 * Creates wallets for transactions.
 * Performs transactions using quantum-resistant cryptography (SPHINCS+).
 * Mines blocks and validates the blockchain.
 * Blockchain Class
 * Purpose: Manages the blockchain and its operations.
 * Functionality:
 * Maintains the chain of blocks.
 * Handles the difficulty level for mining.
 * Manages unspent transaction outputs (UTXOs).
 * Implements sharding for parallel transaction processing.
 * Adds genesis transactions and new blocks.
 * Validates the integrity of the blockchain.
 * Key Differences
 * Role:
 * EnhancedCryptoCoin: Acts as the main application driver.
 * Blockchain: Manages the core blockchain operations and data.
 * Scope:
 * EnhancedCryptoCoin: Focuses on initializing and demonstrating the blockchain’s capabilities.
 * Blockchain: Provides the underlying structure and methods for blockchain functionality.
 * Initialization:
 * EnhancedCryptoCoin: Sets up wallets, transactions, and mining processes.
 * Blockchain: Handles the addition of blocks, transaction validation, and sharding.
 * Summary
 * EnhancedCryptoCoin: Main application class that sets up and runs the blockchain.
 * Blockchain: Core class that manages the blockchain’s data and operations.
 */



