package test;

import cz.zcu.kiv.WorkflowDesigner.Annotations.*;
import static cz.zcu.kiv.WorkflowDesigner.Type.NUMBER;
import static cz.zcu.kiv.WorkflowDesigner.Type.STRING;
import static cz.zcu.kiv.WorkflowDesigner.WorkflowCardinality.ONE_TO_ONE;

@BlockType(type ="ARITHMETIC", family = "MATH")
public class TestBlock {

    @BlockInput(name = "operand_1", type = NUMBER, cardinality = ONE_TO_ONE)
    private int op1=10;

    @BlockInput(name = "operand_2", type = NUMBER, cardinality = ONE_TO_ONE)
    private int op2=7;

    @BlockOutput(name = "operand_1", type = NUMBER, cardinality = ONE_TO_ONE)
    private int op3=0;

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
}
