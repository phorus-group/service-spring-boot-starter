package group.phorus.service.bdd

import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(classes = [TestApp::class])
@CucumberContextConfiguration
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class CucumberSpringConfiguration

@SpringBootApplication(scanBasePackages = ["group.phorus"])
class TestApp