package cz.zcu.kiv.WorkflowDesigner.Annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface BlockType {
    String type();

    String family();

    String description() default "";

    boolean runAsJar() default true;

    boolean jarRMI() default true;
}
