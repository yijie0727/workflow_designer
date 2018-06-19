package test;

import cz.zcu.kiv.WorkflowDesigner.Annotations.*;
import static cz.zcu.kiv.WorkflowDesigner.Type.NUMBER;
import static cz.zcu.kiv.WorkflowDesigner.Type.STRING;
import static cz.zcu.kiv.WorkflowDesigner.WorkflowCardinality.ONE_TO_MANY;
import static cz.zcu.kiv.WorkflowDesigner.WorkflowCardinality.ONE_TO_ONE;

@BlockType(type ="ARITHMETIC", family = "MATH")
public class ArithmeticBlock {

    @BlockInput(name = "Operand1", type = NUMBER, cardinality = ONE_TO_ONE)
    private int op1=0;

    @BlockInput(name = "Operand2", type = NUMBER, cardinality = ONE_TO_ONE)
    private int op2=0;

    @BlockOutput(name = "Operand3", type = NUMBER, cardinality = ONE_TO_MANY)
    private int op3=0;

    @BlockProperty(name ="Operation", type = STRING ,defaultValue = "add")
    private String operation;

    @BlockExecute
    public String process(){
        switch (operation){
            case "add":
                op3=op1+op2;
                break;
            case "subtract":
                op3=op1-op2;
                break;
            case "multiply":
                op3=op1*op2;
                break;
            case "divide":
                op3=op1/op2;
                break;
        }
        return String.valueOf(op3);
    }
}
