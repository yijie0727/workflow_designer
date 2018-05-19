package test;

import cz.zcu.kiv.WorkflowDesigner.Annotations.*;

import static cz.zcu.kiv.WorkflowDesigner.Type.NUMBER;
import static cz.zcu.kiv.WorkflowDesigner.Type.STRING;
import static cz.zcu.kiv.WorkflowDesigner.WorkflowCardinality.ONE_TO_ONE;

@BlockType(type ="CONSTANT", family = "MATH")
public class ConstantBlock {


    @BlockProperty(name = "value", type = NUMBER, defaultValue = "0")
    private int val=7;

    @BlockOutput(name = "operand", type = NUMBER, cardinality = ONE_TO_ONE)
    private int op=0;

    @BlockExecute
    public void process(){
        op=val;
    }
}
