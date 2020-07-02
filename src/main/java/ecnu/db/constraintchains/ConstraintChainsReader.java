package ecnu.db.constraintchains;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;


public class ConstraintChainsReader {


    public List<ConstraintChain> read(String cardinalityConstraintsInput) {
        List<ConstraintChain> constraintChains = new ArrayList<ConstraintChain>();

        String inputLine = null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new
                FileInputStream(cardinalityConstraintsInput)))) {
            // mark the basic filter operation
            int id = 0;
            while ((inputLine = br.readLine()) != null) {
                // skip the blank lines and comments
                if (inputLine.matches("[\\s]*") || inputLine.matches("[ ]*##[\\s\\S]*")) {
                    continue;
                }
                // convert the input line to lower case and remove the spaces and tabs
                inputLine = inputLine.toLowerCase();
                inputLine = inputLine.replaceAll("[ \\t]+", "");

                String[] chainInfoArr = inputLine.split(";");
                String tableName = chainInfoArr[0].substring(1, chainInfoArr[0].length() - 1);
                List<CCNode> nodes = new ArrayList<CCNode>();

                for (int i = 1; i < chainInfoArr.length; i++) {
                    chainInfoArr[i] = chainInfoArr[i].substring(1, chainInfoArr[i].length() - 1);
                    String[] nodeInfoArr = chainInfoArr[i].split(",");

                    // filter node
                    if (nodeInfoArr[0].equals("0") && nodeInfoArr.length == 3) {
                        float probability = Float.parseFloat(nodeInfoArr[2]);
                        String[] filterInfoArr = nodeInfoArr[1].split("#");
                        int logicalRelation = -1;
                        FilterOperation[] filterOperations = null;
                        // there are multiple basic filter operation in this filter node
                        if (filterInfoArr.length != 1) {
                            String logicalRelationStr = filterInfoArr[filterInfoArr.length - 1];
                            if (logicalRelationStr.equals("and")) {
                                logicalRelation = 0;
                            } else if (logicalRelationStr.equals("or")) {
                                logicalRelation = 1;
                            } else {
//								logger.error("\n\tUnsupported logical relation: " + logicalRelationStr
//										+ ", " + chainInfoArr[i]);
                                System.exit(0);
                            }
                            filterOperations = new FilterOperation[filterInfoArr.length - 1];
                            float filterOperationProbability = getFilterOperationProbability(logicalRelation,
                                    filterInfoArr.length - 1, probability);
                            for (int j = 0; j < filterInfoArr.length - 1; j++) {
                                filterOperations[j] = newFilterOperation(id++, filterInfoArr[j], filterOperationProbability);
                            }
                        } else { // only one basic filter operation
                            filterOperations = new FilterOperation[1];
                            filterOperations[0] = newFilterOperation(id++, filterInfoArr[0], probability);
                        }
                        Filter filter = new Filter(filterOperations, logicalRelation, probability);
                        CCNode node = new CCNode(0, filter);
                        nodes.add(node);

                        // PKJoin node
                    } else {
//						logger.error("\n\tUnable to parse the constraint chain information: " + chainInfoArr[i]);
                        System.exit(0);
                    }
                }
                constraintChains.add(new ConstraintChain(tableName, nodes));
            }
        } catch (Exception e) {
            System.out.println("\n\tError input line: " + inputLine);
            e.printStackTrace();
            System.exit(0);
        }
//		logger.debug("\nThe cardinality constraint chains: " + constraintChains);
        return constraintChains;
    }

    // assume that the probability of each basic filter operation is the same
    private float getFilterOperationProbability(int logicalRelation, int size, float probability) {
        if (logicalRelation == 0) {
            return (float) Math.pow(probability, (double) 1 / size);
        } else { // logicalRelation == 1
            return (float) (1 - Math.pow(1 - probability, (double) 1 / size));
        }
    }

    // <> -> =, not like -> like, not in -> in
    private FilterOperation newFilterOperation(int id, String filterOperationInfo, float probability) {
        String[] arr = filterOperationInfo.split("@");
        if (arr[1].equals("<>")) {
            arr[1] = "=";
            probability = 1 - probability;
        } else if (arr[1].equals("notlike")) {
            arr[1] = "like";
            probability = 1 - probability;
        } else if (arr[1].matches("notin\\([0-9]+\\)")) {
            arr[1] = arr[1].substring(arr[1].indexOf('i'));
            probability = 1 - probability;
        }
        return new FilterOperation(id, arr[0], arr[1], probability);
    }

}
