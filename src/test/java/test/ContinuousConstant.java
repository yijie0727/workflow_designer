package test;

import cz.zcu.kiv.WorkflowDesigner.Annotations.*;

import java.io.Serializable;

import static cz.zcu.kiv.WorkflowDesigner.Type.NUMBER;

@BlockType(type ="ContinuousCONSTANT", family = "MATH")
public class ContinuousConstant implements Serializable {


    @BlockProperty(name = "Value", type = NUMBER, defaultValue = "0")
    private int val=7;

    @BlockOutput(name = "Operand", type = NUMBER)
    private int op=0;

    @ContinuousProperty(name = "continuousFlag")
    private boolean continuousFlag = true;

    @ContinuousGet
    public boolean isContinuousFlag() {
        return continuousFlag;
    }

    public void setContinuousFlag(boolean continuousFlag) {
        this.continuousFlag = continuousFlag;
    }

    @BlockExecute
    public void process(){
        op=val;
        //continuousFlag = false;
        setContinuousFlag(false);
    }
}
