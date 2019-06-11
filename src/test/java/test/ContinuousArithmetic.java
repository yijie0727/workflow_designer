package test;

import cz.zcu.kiv.WorkflowDesigner.Annotations.*;

import java.io.Serializable;

import static cz.zcu.kiv.WorkflowDesigner.Type.NUMBER;
import static cz.zcu.kiv.WorkflowDesigner.Type.STRING;

@BlockType(type ="ContinuousARITHMETIC", family = "MATH")
public class ContinuousArithmetic implements Serializable {

    @BlockInput(name = "Operand1", type = NUMBER)
    private int op1=0;

    @BlockInput(name = "Operand2", type = NUMBER)
    private int op2=0;

    @BlockOutput(name = "Operand3", type = NUMBER)
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
