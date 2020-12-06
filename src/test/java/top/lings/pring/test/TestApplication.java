package top.lings.pring.test;

import javafx.stage.Stage;
import top.lings.pring.PringApplication;
import top.lings.pring.annotation.ComponentScan;
import top.lings.pring.test.component.TestPane;

@ComponentScan({"top.lings.pring.test"})
public class TestApplication extends PringApplication {
    @Override
    public void start(Stage primaryStage) {
        TestPane testPane = getBean(TestPane.class);
        testPane.h();
    }
}
