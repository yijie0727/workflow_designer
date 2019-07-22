package test;

import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockExecute;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockInput;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockType;
import cz.zcu.kiv.WorkflowDesigner.Visualizations.Table;

import java.io.ObjectInputStream;
import java.io.PipedInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static cz.zcu.kiv.WorkflowDesigner.Type.STREAM;

@BlockType(type ="TargetPet", family = "PIPE")
public class PetTarget {

    @BlockInput(name = "TargetPets", type = STREAM)
    PipedInputStream pipedIn1 = new PipedInputStream();


    @BlockExecute
    public Table process() throws Exception{

        ObjectInputStream objectInputStream = new ObjectInputStream(pipedIn1);

        Pet pet;
        String petType = null;
        Table table = new Table();
        List<List<String>> rows = new ArrayList<>();
        List<String> colHead = new ArrayList<>();
        while((pet = (Pet) objectInputStream.readObject())!= null){
            rows.add(Arrays.asList( pet.getName() ));

            if(petType == null){
                petType = pet.getType();
                colHead.add("All "+petType+ "s name:");
            }
        }
        table.setColumnHeaders(colHead);
        table.setRows(rows);
        table.setCaption(petType+ "s Table");

        pipedIn1.close();
        objectInputStream.close();
        return table;
    }



}
