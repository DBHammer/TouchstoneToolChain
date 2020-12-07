package ecnu.db.constraintchain.arithmetic.value;

import ecnu.db.constraintchain.arithmetic.ArithmeticNode;
import ecnu.db.constraintchain.arithmetic.ArithmeticNodeType;
import ecnu.db.schema.ColumnManager;

/**
 * @author wangqingshuai
 */
public class ColumnNode extends ArithmeticNode {
    private String canonicalColumnName;

    public ColumnNode() {
        super(ArithmeticNodeType.COLUMN);
    }

    public void setCanonicalColumnName(String canonicalColumnName) {
        this.canonicalColumnName = canonicalColumnName;
    }

    public String getCanonicalColumnName() {
        return canonicalColumnName;
    }

    @Override
    public double[] calculate() {
        return ColumnManager.getInstance().calculate(canonicalColumnName);
    }

    @Override
    public String toString() {
        return canonicalColumnName;
    }
}
