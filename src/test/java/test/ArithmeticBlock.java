package test;

import cz.zcu.kiv.WorkflowDesigner.Annotations.*;
import static cz.zcu.kiv.WorkflowDesigner.Type.NUMBER;
import static cz.zcu.kiv.WorkflowDesigner.Type.STRING;
import static cz.zcu.kiv.WorkflowDesigner.WorkflowCardinality.ONE_TO_MANY;
import static cz.zcu.kiv.WorkflowDesigner.WorkflowCardinality.ONE_TO_ONE;

@BlockType(type ="ARITHMETIC", family = "MATH")
public class ArithmeticBlock {

    @BlockInput(name = "operand1", type = NUMBER, cardinality = ONE_TO_ONE)
    private int op1=0;

    @BlockInput(name = "operand2", type = NUMBER, cardinality = ONE_TO_ONE)
    private int op2=0;

    @BlockOutput(name = "operand3", type = NUMBER, cardinality = ONE_TO_MANY)
    private static int op3=0;

    @BlockProperty(name ="operation", type = STRING ,defaultValue = "add")
    private String operation;

    @BlockExecute
    public void process(){
        switch (operation){
            case "add":
                op3=op1+op2;
                break;
            case "subtract":
                op3=op1-op2;
                break;
        }
    }

    public static int getOp3() {
        return op3;
    }
}
