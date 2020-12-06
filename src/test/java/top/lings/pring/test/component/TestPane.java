package top.lings.pring.test.component;

import top.lings.pring.annotation.Autowired;
import top.lings.pring.annotation.Component;
import top.lings.pring.test.controller.TestController;

@Component
public class TestPane {
    @Autowired
    public TestController testController;
    @Autowired
    public TwoPane twoPane;

    public void h() {
        System.out.println(testController);
        System.out.println(twoPane);
        testController.click();
    }
}
