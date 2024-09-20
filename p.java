import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.TypeReference;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AutoTransferXAI {

    public static void main(String[] args) throws Exception {
        // Initialize API Endpoint and Wallet information
        String alchemyUrl = "https://eth-arbitrum.alchemyapi.io/v2/YOUR_ALCHEMY_API_KEY"; // Replace with your Alchemy API Key
        Web3j web3j = Web3j.build(new HttpService(alchemyUrl));

        // Retrieve private key from environment variable
        String privateKey = System.getenv("PRIVATE_KEY");
        Credentials credentials = Credentials.create(privateKey);

        // Wallet addresses
        String fromAddress = credentials.getAddress();
        String toAddress = "0xYourDestinationAddress"; // Replace with your destination address

        // XAI Token Contract Address
        String xaiContractAddress = "0xYourXAIContractAddress"; // Replace with XAI contract address

        // Minimum amount to transfer (e.g., 10 XAI)
        BigInteger minAmountToSend = BigInteger.valueOf(10).multiply(BigInteger.TEN.pow(18));

        while (true) {
            // Checking XAI token balance in the wallet
            BigInteger xaiBalance = getTokenBalance(web3j, xaiContractAddress, fromAddress);
            System.out.println("Current XAI Balance: " + xaiBalance);

            if (xaiBalance.compareTo(minAmountToSend) >= 0) {
                // If balance is sufficient, proceed with the transfer
                transferToken(web3j, credentials, xaiContractAddress, toAddress, minAmountToSend);
            } else {
                System.out.println("Insufficient balance. Checking again...");
            }

            // Sleep for a while before checking the balance again
            Thread.sleep(1200); // Check every 1.2 seconds
        }
    }

    // Retrieve XAI token balance
    public static BigInteger getTokenBalance(Web3j web3j, String contractAddress, String ownerAddress) throws Exception {
        Function function = new Function(
                "balanceOf",
                Collections.singletonList(new org.web3j.abi.datatypes.Address(ownerAddress)),
                Collections.singletonList(new TypeReference<Uint256>() {})
        );
        String responseValue = web3j.ethCall(Transaction.createEthCallTransaction(ownerAddress, contractAddress, FunctionEncoder.encode(function)), DefaultBlockParameterName.LATEST).send().getValue();
        List<Type> response = FunctionReturnDecoder.decode(responseValue, function.getOutputParameters());
        return (BigInteger) response.get(0).getValue();
    }

    // Transfer XAI token
    public static void transferToken(Web3j web3j, Credentials credentials, String contractAddress, String toAddress, BigInteger amount) throws Exception {
        // Get nonce
        EthGetTransactionCount ethGetTransactionCount = web3j.ethGetTransactionCount(
                credentials.getAddress(), DefaultBlockParameterName.LATEST).send();
        BigInteger nonce = ethGetTransactionCount.getTransactionCount();

        // Define gas price and limit
        BigInteger gasPrice = BigInteger.valueOf(20000000000L); // Setting gas price to 20 Gwei
        BigInteger gasLimit = BigInteger.valueOf(60000); // Appropriate gas limit for token transfer

        // Create transfer function
        Function function = new Function(
                "transfer",
                Arrays.asList(new org.web3j.abi.datatypes.Address(toAddress),
                              new Uint256(amount)),
                Collections.emptyList()
        );
        String encodedFunction = FunctionEncoder.encode(function);

        // Create raw transaction
        RawTransaction rawTransaction = RawTransaction.createTransaction(
                nonce, gasPrice, gasLimit, contractAddress, encodedFunction);

        // Sign the transaction
        byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
        String hexValue = Numeric.toHexString(signedMessage);

        // Send transaction
        EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();

        // Show transaction result
        String transactionHash = ethSendTransaction.getTransactionHash();
        if (transactionHash != null) {
            System.out.println("Transaction successfully sent. Transaction Hash: " + transactionHash);
        } else {
            System.out.println("Error sending transaction: " + ethSendTransaction.getError().getMessage());
        }
    }
}