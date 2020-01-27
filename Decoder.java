import java.lang.reflect.InvocationTargetException;
import java.util.*;
public class Decoder {

    private FullOpcodeList list = new FullOpcodeList();
    private String address = "0xE0e9794A17aa5166c164b80fA0b126c72E5412B0";
    private String bytecode = "0x68466bad7e2343211320d5dcc03764c0ba522ad7aa22a9076d94f7a7519121dcaabbss1122ab01";
    //private String bytecode = "0x608060405234801561001057600080fd5b506040516020806103ee833981018060405281019080805190602001909291905050508060008190555080600160003373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190815260200160002081905550506103608061008e6000396000f300608060405260043610610057576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff16806318160ddd1461005c57806370a0823114610087578063a9059cbb146100de575b600080fd5b34801561006857600080fd5b50610071610143565b6040518082815260200191505060405180910390f35b34801561009357600080fd5b506100c8600480360381019080803573ffffffffffffffffffffffffffffffffffffffff16906020019092919050505061014c565b6040518082815260200191505060405180910390f35b3480156100ea57600080fd5b50610129600480360381019080803573ffffffffffffffffffffffffffffffffffffffff16906020019092919080359060200190929190505050610195565b604051808215151515815260200191505060405180910390f35b60008054905090565b6000600160008373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff168152602001908152602001600020549050919050565b60008073ffffffffffffffffffffffffffffffffffffffff168373ffffffffffffffffffffffffffffffffffffffff16141515156101d257600080fd5b600160003373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190815260200160002054821115151561022057600080fd5b81600160003373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020016000205403600160003373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020016000208190555081600160008573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020016000205401600160008573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020016000208190555060019050929150505600a165627a7a7230582025608b5c372888ca7316c16f1748775824bc03cb64852867e119a10a03402ef500290000000000000000000000000000000000000000000000000000000000000000";
    private List<String> theOpcodes;
    //HashMap contains [Code, Name], [added to stack, removed from stack]
    private HashMap<List<String>, List<String>> decoded;
    public final List<Opcode> allOpcodes;
    public Stack stack, memory;
    private StopAndArithmetic completeArithmeticOps;

    public Decoder() {
        decoded = new HashMap<List<String>, List<String>>();
        FormatBytecode formatter = new FormatBytecode(decoded);
        List<String> finished = new ArrayList<String>();
        finished = formatter.getFormattedData();
        finished.forEach(current -> System.out.print(current + "\n"));
        theOpcodes = new ArrayList<String>();
        stack = new Stack();
        memory = new Stack();
        allOpcodes = list.getList();
        completeArithmeticOps = new StopAndArithmetic();

        //Remove leading 0x in any input bytecode
        if(bytecode.startsWith("0x")) {
            bytecode = bytecode.substring(2);
        }
        splitCode();
        //System.out.println(Arrays.toString(theOpcodes.toArray()));
        decodeBytecode();
        //System.out.println(Arrays.asList(decoded));
    }

    private void splitCode() {
        int j = 0;
        int last = (int) (bytecode.length() / 2) - 1;
        for (int i = 0; i < last; i++) {
            theOpcodes.add(bytecode.substring(j, j + 2));
            j += 2;
        }
        theOpcodes.add(bytecode.substring(j));

    }

    private void decodeBytecode() {
        List<String> additionalData;
        while(theOpcodes.size() > 0) {
            List<String> tempList = new ArrayList<String>();
            String opcodeName = findOpcodeName(theOpcodes.get(0));
            if(opcodeName != null) {
                tempList.add(theOpcodes.get(0));
                tempList.add(opcodeName);
                additionalData = getAdditionalInfo(theOpcodes.get(0));
                decoded.put(tempList, additionalData);
            }
            theOpcodes.remove(0);
            //Print out the stack
            //System.out.println(Arrays.toString(stack.getStack().toArray()));;
        }
    }

    private String findOpcodeName(String opcode) {
        try {
            int counter = 0;
            while (counter < allOpcodes.size()) {
                if (allOpcodes.get(counter).getCode().matches(opcode)) {
                    return allOpcodes.get(counter).getName();
                }
                counter++;
            }
            throw new UnknownOpcodeException("Invalid opcode: " + opcode);
        }catch(UnknownOpcodeException e) {
            e.printStackTrace();
            return null;
        }
    }

    private List<String> getAdditionalInfo(String theCode) throws RuntimeException {
        List<String> stackAmendments = new ArrayList<String>();
        int counter = 0;
        int noBytes = Character.digit((theCode.charAt(theCode.length() - 1)), 16);
        char switchChar = theCode.charAt(0);
        switch(switchChar) {
            case '0':
                stackAmendments = arithmeticOp(theCode);
                break;
            case '1':
                //Comparison Operations
                stackAmendments = doComparison(theCode);
                break;
            case '2':
                stackAmendments.add("None");
                stackAmendments.add("None");
                //SHA3
                break;
            case '3':
                doEnvironmentalOps(theCode);
                stackAmendments.add("None");
                stackAmendments.add("None");
                //Environmental Operations
                break;
            case '4':
                stackAmendments.add("None");
                stackAmendments.add("None");
                //Block Operations
                break;
            case '5':
                //Memory, Storage and Flow Operations
                if(theCode.matches("50")) {
                    stack.pop();
                    stackAmendments.add("None");
                    stackAmendments.add(stack.get(0));
                }else {
                    stackAmendments = stackOperations(theCode);
                }
                break;
            case '6':
            case '7':
                //Push Operations
                StringBuilder sb = new StringBuilder();
                while(counter <= noBytes) {
                    sb.append(theOpcodes.get(counter)).append(" ");
                    stack.push(theOpcodes.get(counter));
                    counter++;
                }
                stackAmendments.add(sb.toString());
                stackAmendments.add("None");
                break;
            case '8':
                //Duplication Operations
                stackAmendments = duplicateStackItem(theCode);
                break;
            case '9':
                //Exchange(Swap) Operations
                stackAmendments = swapStackItems(theCode);
                break;
            case 'a':
                stackAmendments.add("None");
                stackAmendments.add("None");
                //Logging Operations
                break;
            case 'f':
                stackAmendments.add("None");
                stackAmendments.add("None");
                //System Operations
                break;
            default:
                throw new RuntimeException("Unreachable");
        }
        return stackAmendments;
    }


    private List<String> doComparison(String theCode) {
        List<String> additionalInfo = new ArrayList<String>();
        ComparisonOperations classRef = new ComparisonOperations();
        String arg1 = stack.get(0);
        String arg2 = stack.get(1);
        String removed = arg1 + ", " + arg2;
        String res = "";
        stack.pop();
        stack.pop();
        String methodName = classRef.getOpcodeName(theCode).toLowerCase();
        try {
            Class<?> myClass = Class.forName("ComparisonOperations");
            java.lang.reflect.Method method = myClass.getDeclaredMethod(methodName, String.class, String.class);
            Object result = method.invoke(classRef, arg1, arg2);
            res = (String) result;
            stack.push(res);
        } catch (NoSuchMethodException | ClassNotFoundException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
        additionalInfo.add(res);
        additionalInfo.add(removed);
        return additionalInfo;
    }

    private List<String> duplicateStackItem(String theCode) {
        List<String> info = new ArrayList<String>();
        int stackNumber = Character.digit((theCode.charAt(theCode.length() - 1)), 16);
        stack.push(stack.get(stackNumber + 1));
        info.add(stack.get(stackNumber + 1));
        info.add("None");
        return info;
    }

    private List<String> swapStackItems(String theCode) {
        List<String> changes = new ArrayList<String>();
        changes.add("None");
        changes.add("None");
        int swapWith = Character.digit((theCode.charAt(theCode.length() - 1)), 16);
        stack.swapElements(swapWith + 1);
        return changes;
    }

    private List<String> arithmeticOp(String theCode) {
        List<String> additionalInfo = new ArrayList<String>();
        String methodToCall = completeArithmeticOps.getOpcodeName(theCode).toLowerCase();
        String arg1 = stack.get(0);
        String arg2 = stack.get(1);
        String removed = arg1 + ", " + arg2;
        try {
            Class<?> myClass = Class.forName("StopAndArithmetic");
            java.lang.reflect.Method method = myClass.getDeclaredMethod(methodToCall, String.class, String.class);
            Object result = method.invoke(completeArithmeticOps, arg1, arg2);
            String res = (String) result;
            stack.pop();
            stack.pop();
            stack.push(res);
            additionalInfo.add(res);
            additionalInfo.add(removed);
        } catch (NoSuchMethodException | ClassNotFoundException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return additionalInfo;
    }

    private List<String> stackOperations(String theCode) {
        List<String> additionalInfo = new ArrayList<String>();
        //String methodToCall = completeArithmeticOps.getOpcodeName(theCode).toLowerCase();
        String memWord;
        if(theCode.matches("51")) {
            additionalInfo.add(memory.get(0));
            additionalInfo.add(stack.get(0));
            stack.replace(0, memory.get(0));
            memory.pop();
        }else if(theCode.matches("52")) {
            memWord = stack.get(0) + stack.get(1);
            additionalInfo.add("None");
            additionalInfo.add(stack.get(0) + ", " + stack.get(1));
            stack.pop();
            stack.pop();
            memory.push(memWord);
        }
        //To do 53, 54, 55, 58, 59, 5a
        return additionalInfo;
    }

    private void invokeMemoryOps(String methodName) {
        try {
            Class<?> myClass = Class.forName("StackMemory");
            java.lang.reflect.Method method = myClass.getDeclaredMethod(methodName);
            method.invoke(completeArithmeticOps);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void doEnvironmentalOps(String theCode) {
        if(theCode.matches("33")) {
            stack.pop();
            stack.push(address);
        }
    }

}