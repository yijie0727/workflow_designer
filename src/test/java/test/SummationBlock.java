package test;

import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockExecute;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockInput;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockType;

import java.util.List;

import static cz.zcu.kiv.WorkflowDesigner.Type.NUMBER_ARRAY;
import static cz.zcu.kiv.WorkflowDesigner.WorkflowCardinality.ONE_TO_MANY;

@BlockType(type ="SUMMATION", family = "MATH")
public class SummationBlock {

    @BlockInput(name = "Operand1", type = NUMBER_ARRAY, cardinality = ONE_TO_MANY)
    private List<Integer> op;

    @BlockExecute
    public String process(){
        int sum=0;
        for(int i=0;i<op.size();i++){
            sum+=op.get(i);
        }
        return String.valueOf(sum);
    }
}
