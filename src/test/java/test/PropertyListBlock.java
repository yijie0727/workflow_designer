package test;

import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockExecute;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockProperty;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockType;
import java.util.List;
import static cz.zcu.kiv.WorkflowDesigner.Type.STRING_ARRAY;

@BlockType(type ="CONCATENATE", family = "STRING")
public class PropertyListBlock {

    @BlockProperty(name = "Strings", type = STRING_ARRAY, defaultValue = "")
    private List<String> op;

    @BlockExecute
    public String process(){
        String string="";
        for(int i=0;i<op.size();i++){
            string+=op.get(i);
        }
        return string;
    }
}
