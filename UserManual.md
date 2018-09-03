# INCF Workflow Designer v1.16
## Basics
A **workflow** is a group of **blocks** connected to each other where the output of one block is the input for the other. Workflows do not necessarily mean a single flow of execution, it could have multiple parallel flows.

Consider this simple workflow to add two constants:

[![N|Solid](http://www.zedacross.com/wp-content/uploads/2018/06/Screen-Shot-2018-06-04-at-12.51.14-PM-300x157.png)](http://zedacross.com/gsoc2018)


Here we have defined two kinds of blocks, a CONSTANT block and an ARITHMETIC block.

In the CONSTANT block, Value is a numeric parameter and its value can be modified. Similarly, in the ARITHMETIC block, Operation is a string parameter and can also be modified.

[![N|Solid](http://www.zedacross.com/wp-content/uploads/2018/06/Screen-Shot-2018-06-04-at-12.55.40-PM-300x228.png)](http://zedacross.com/gsoc2018)

On executing the above workflow, the result is produced by the arithmetic block as shown here.

[![N|Solid](http://www.zedacross.com/wp-content/uploads/2018/06/Screen-Shot-2018-06-04-at-12.58.21-PM-300x173.png)](http://zedacross.com/gsoc2018)

To define a **block** we need to specify the inputs, outputs, properties and the execution function.

The execution function may use the inputs and the properties to perform some operation and produce a single visualization and multiple outputs variables. The visualization"Previous Output" can be used to see intermediate results. The outputs (Operand3) can then be forwarded as an input to another block.

### Mapping into Java:
To define a block, as shown above, we use annotations from our own library. The annotations help the workflow designer construct a block by defining its inputs, outputs, properties and the execution routine.

The following are the types of annotations and their parameters:

#### BlockType
A block type is a CLASS annotation. It is used to define a single block in the workflow. A BlockType must have the following attributes:
1. type: A string constant to identify the block type
2. family: A string constant to group similar blocks together
3. runAsJar: Optional boolean attribute to indicate whether a block must be executed externally via its jar file. Use this parameter when executing blocks from libraries having too many dependencies or complicated classpath permissions. For example, Apache Spark, Spring projects may have configuration parameters that cannot be accessed using reflections. Use this boolean to call blocks directly from the jar. Starting v1.16 this is set to be true by default. This is recommended to prevent conflicts while loading multiple JAR files into the classpath.
4. description: Optional string attribute to be displayed on the 'i' (info) icon on each block. Use this for explaining complicated blocks.

#### BlockInput
A BlockInput is a CLASS VARIABLE annotation. Input parameters for a block can be annotated using this to indicate that the block accepts the inputs. It must have the following attributes:
1. name: A string constant to identify the input parameter. Case-sensitive, no spaces or special characters are allowed.
2. type: A unique string constant defined by the user to identify the object type of the input. This attribute is used to validate connections between inputs and outputs. The type of an input an output **must** match to create a connection between them.

#### BlockProperty
A BlockProperty is a CLASS VARIABLE annotation. These are runtime parameters for blocks and can be modified without changing the workflow. It must have the following parameters:
1. name: Unique string constant in the class to identify the property. (Supports spaces, special symbols)
2. type: A string constant that can be either "STRING" or "NUMBER" or "BOOLEAN" or as the constant defined in the **cz.zcu.kiv.WorkflowDesigner.Type** package.
3. defaultValue: Has to be a String value to be used to initialize the property.

#### BlockOuput

A BlockOuput is a CLASS VARIABLE annotation that indicates an output variable that will hold the output value after execution is complete. It must have the following attributes:
1. name: A string constant to identify the output parameter. No spaces or special characters are allowed.
2. type: A unique string constant defined by the user to identify the object type of the output. This attribute is used to validate connections between inputs and outputs.

#### BlockExecute
A BlockExecute is a METHOD annotation that indicates the method to be called when a block needs to be executed. A block execute has no attributes.

A BlockExecute must perform some operations being able to access the input parameters and properties. It should also assign the output variables with their proper value due operations are complete. It may return a String, A File, A Table, A Graph or null/void. These return types are especially useful while using the **workflow designer server** project.
### Example:
```java
package data;

import cz.zcu.kiv.WorkflowDesigner.Annotations.*;
import static cz.zcu.kiv.WorkflowDesigner.Type.NUMBER;
import static cz.zcu.kiv.WorkflowDesigner.WorkflowCardinality.ONE_TO_MANY;

@BlockType(type ="CONSTANT", family = "MATH", runAsJar = true)
public class ConstantBlock {


    @BlockProperty(name = "Value", type = NUMBER, defaultValue = "0")
    private int val=7;

    @BlockOutput(name = "Operand", type = NUMBER, cardinality = ONE_TO_MANY)
    private int op=0;

    @BlockExecute
    public void process(){
        op=val;
    }
}
```

```java
package data;

import cz.zcu.kiv.WorkflowDesigner.Annotations.*;
import cz.zcu.kiv.WorkflowDesigner.Table;
import org.apache.commons.io.FileUtils;
import static cz.zcu.kiv.WorkflowDesigner.Type.NUMBER;
import static cz.zcu.kiv.WorkflowDesigner.Type.STRING;
import static cz.zcu.kiv.WorkflowDesigner.WorkflowCardinality.ONE_TO_MANY;
import static cz.zcu.kiv.WorkflowDesigner.WorkflowCardinality.ONE_TO_ONE;

@BlockType(type ="ARITHMETIC", family = "MATH", runAsJar = true)
public class ArithmeticBlock {

    @BlockInput(name = "Operand1", type = NUMBER, cardinality = ONE_TO_ONE)
    private int op1=0;

    @BlockInput(name = "Operand2", type = NUMBER, cardinality = ONE_TO_ONE)
    private int op2=0;

    @BlockOutput(name = "Operand3", type = NUMBER, cardinality = ONE_TO_MANY)
    private static int op3=0;

    @BlockProperty(name ="Operation", type = STRING ,defaultValue = "add")
    private String operation;

    @BlockExecute
    public String process() throws IOException {
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
        }

        return String.valueOf(op3);
    }
}
```

Once these annotations are provided to a class. The project can then be used to run workflows consisting blocks of that class.
### Defining a workflow
The easiest way to create a workflow is using the **workflow designer server project** which provides a visual drag-and-drop interface to create workflows. However, it is also possible to manually define a workflow using the following JSON structure:
```javascript
{
    //List of blocks
    "blocks": [
        {
            "id": 1,                   //Block ID (Integer)
            "type": "ARITHMETIC",      //Block type (String)
            "module": "test.jar:test", //Module of the block (JarName:Package)
            "values": {                //Values of properties
                "Operation": "add"
            }
        },
        {
            "id": 2,
            "type": "CONSTANT",
            "module": "test.jar:test",
            "values": {
                "Value": "10"
            }
        },
        {
            "id": 3,
            "type": "CONSTANT",
            "module": "test.jar:test",
            "values": {
                "Value": "5"
            }
        }
    ],
//List of Edges (Connections between blocks)
"edges": [
    {
        "id": 1,          //Edge id (Integer)
        "block1": 3,      //Connection from block id (Integer)
        "connector1": [
            "Operand",    //From Block type
            "output"      //From Field name
        ],
        "block2": 1,      //Connection to block id   (Integer)
        "connector2": [
            "Operand2",   //To block type
            "input"       //To Field name
        ]
    },
    {
        "id": 2,
        "block1": 2,
        "connector1": [
            "Operand",
            "output"
        ],
        "block2": 1,
        "connector2": [
            "Operand1",
            "input"
        ]
    }
]
}
```
### Block Execute Return Types:
#### File

A block with a BlockExecute method of the type: **public java.io.File process(){}** can be used when the result of a block needs to be presented to the user as a file.

Create the file at any location and return a java.io.File reference to the file. The workflow designer will automatically move it to the configured location and will send the filename to the output JSON.

#### String

A simple output of type String can be returned and will be added to the output JSON as is.

#### Table

A table data type with native support in the workflow designer server project. The table data structure uses a List&lt;String&gt; for column headers and row headers. The rows are stored as List&lt;List&lt;String&gt;&gt;

Here is an example of a three column table being returned. Another advantage of using tables is that they are exported to a .csv file which can be opened in any spreadsheet application.
```java
@BlockExecute
    public Table process(){
        Table table = new Table();
        table.setColumnHeaders(
             Arrays.asList(new String[]{"Multiplier","Multiplicand","Product"}));
        List&lt;List&lt;String&gt;&gt;rows=new ArrayList&lt;&gt;();
        for(int i=1;i&lt;=multiplicand;i++){
            List&lt;String&gt;columns=Arrays.asList(
                    new String[]{
                            String.valueOf(multiplier),
                            String.valueOf(i),
                            String.valueOf(multiplier*i)
                    });
            rows.add(columns);
        }
        table.setRows(rows);
        table.setCaption("Multiplication Table");
        return table;
    }

```
#### Graph

A graph is especially useful when large amounts of information need to be plotted as a result of a block. The graph return type from the workflow designer has native support to be visualized in the workflow_designer_server project and as a JSON file in the output json.

To support plotly.js visualization the following data structure is used to define a graph.

Example:

The following example reads a CSV file and plots an X-Y graph from it
```java
@BlockExecute
public Graph process() throws IOException {
    Graph graph = new Graph();
    List&lt;Trace&gt; traces=new ArrayList&lt;&gt;();
    Trace trace = new Trace();
    trace.setGraphType(GraphType.SCATTER);
    Marker marker=new Marker();
    marker.setSize(12.0);
    trace.setMarker(marker);

    List&lt;Point&gt;points=new ArrayList&lt;&gt;();
    String csv=FileUtils.readFileToString(csvFile,Charset.defaultCharset());

    String rows[]=csv.split("\\n");

    for(int i=hasHeaders?1:0;i&lt;rows.length;i++){
        String columns[]=rows[i].split(",");
        Double x=Double.parseDouble(columns[0]);
        Double y=Double.parseDouble(columns[1]);
        points.add(new Point(new Coordinate(x,y)));
    }

    trace.setPoints(points);
    trace.setTraceMode(TraceMode.MARKERS_ONLY);
    traces.add(trace);
    graph.setTraces(traces);

    Layout layout = new Layout();
    layout.setTitle(csvFile.getName());

    Axis xAxis=new Axis();
    xAxis.setMin(0);
    xAxis.setMax(100);

    Axis yAxis=new Axis();
    yAxis.setMin(0);
    yAxis.setMax(100);

    layout.setXaxis(xAxis);
    layout.setYaxis(yAxis);

    graph.setLayout(layout);
    this.graph=graph;
    return graph;
}
```
### Executing a workflow
Again, the workflow designer server project provides a one-click option to execute a workflow, one can run it directly using the following snippet. This is an example of a test defined to validate the arithmetic and constant blocks created above. The file "test.json" contains the workflow to add two numbers 5 and 10 as show in the "defining a workflow" section.

The constructor for a workflow has the following parameters:
```java
Workflow(ClassLoader classLoader, //Classloader to use for reflection
String module,    //Name of the module to execute jarName(Optional)
String packageName, //The package name is important for reflection scans
String jarDirectory,     //Location of jarFile
String remoteDirectory) //Location of files to read as input
```
When running from within the classpath, the jarDirectory and remoteDirectory are nullable. The execute method has the following parameters
```java
public JSONArray execute(
JSONObject workflow// Workflow JSON
, String outputFolder //Directory to place outputFiles in
, String workflowOutputFile //File to write output updates to)
```
The workflowOutputFile, if provided, will at any point of time contain the latest status of the workflow execution.
```java
@Test
public void testJSONArithmetic() throws IOException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, FieldMismatchException {
     //Read JSON workflow from file
    String json = FileUtils.readFileToString(new File("test_data/test.json") ,Charset.defaultCharset());
    JSONObject jsonObject = new JSONObject(json);

     //Create a temp file to store output
    File outputFile = File.createTempFile("testJSONArithmetic",".json");
    outputFile.deleteOnExit();
//===================================================
    //Create workflow instance
    Workflow workflow = new Workflow(ClassLoader.getSystemClassLoader(), ":test",null,"")
//===================================================
    //Execute workflow
    JSONArray jsonArray = workflow.execute (jsonObject,"test_data",outputFile.getAbsolutePath());

    assert jsonArray !=null;
    assert jsonArray.getJSONObject(0).getJSONObject("output").getInt("value")==15;
    assert jsonArray.length() == 3;
}
```
After execution jsonArray contains
```javascript
[
    {
        "output": {
            "type": "STRING",  //Type of output can be string,file,graph,table
            "value": "15"      //result of 5+10
        },
        "stdout": "",          //Standard output stream
        "module": "test.jar:test",
        "values": {"Operation": "add"},
        "id": 1,
        "completed": true,     //Whether execution is complete
        "type": "ARITHMETIC",
        "error": false,        //Whether there was an error during exectuion
        "stderr": ""           //Contents of std error stream
    },
    {
        "stdout": "",
        "module": "test.jar:test",
        "values": {"Value": "10"},
        "id": 2,
        "completed": true,
        "type": "CONSTANT",
        "error": false,
        "stderr": ""
    },
    {
        "stdout": "",
        "module": "test.jar:test",
        "values": {"Value": "5"},
        "id": 3,
        "completed": true,
        "type": "CONSTANT",
        "error": false,
        "stderr": ""
    }
]
```
