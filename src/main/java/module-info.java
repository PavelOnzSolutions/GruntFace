module solutions.onz.toolbox.gruntface {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.prefs;
    requires java.desktop;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.feather;
    requires atlantafx.base;
    requires org.fxmisc.richtext;
    requires org.fxmisc.flowless;
    requires reactfx;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    requires org.eclipse.elk.core;
    requires org.eclipse.elk.alg.layered;
    requires org.eclipse.elk.graph;
    requires org.eclipse.xtext.xbase.lib;
    requires org.slf4j;

    requires hcl4j;
    requires org.commonmark;

    exports solutions.onz.toolbox.gruntface.app;
    exports solutions.onz.toolbox.gruntface.model;
    exports solutions.onz.toolbox.gruntface.hcl;
    exports solutions.onz.toolbox.gruntface.discovery;
    exports solutions.onz.toolbox.gruntface.name;

    exports solutions.onz.toolbox.gruntface.ui.graph;

    opens solutions.onz.toolbox.gruntface.ui to javafx.fxml;
    exports solutions.onz.toolbox.gruntface.ui;

    opens solutions.onz.toolbox.gruntface.ui.create to javafx.fxml;
    exports solutions.onz.toolbox.gruntface.ui.create;

    opens solutions.onz.toolbox.gruntface.model to javafx.base;
}
