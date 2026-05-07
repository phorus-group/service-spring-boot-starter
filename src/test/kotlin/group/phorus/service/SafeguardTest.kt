package group.phorus.service

import group.phorus.service.bdd.app.controllers.AddressController
import group.phorus.service.bdd.app.controllers.UserController
import group.phorus.service.bdd.app.dtos.UserDTO
import group.phorus.service.bdd.app.dtos.UserResponse
import group.phorus.service.bdd.app.model.User
import group.phorus.service.bdd.app.services.impl.AddressServiceImpl
import group.phorus.service.controller.SimpleCrudController
import group.phorus.service.service.CrudServiceDependencyResolver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


class SafeguardTest {

    @Test
    fun `auto-resolver fails when JpaRepository is missing for a CrudController entity`() {
        val factory = DefaultListableBeanFactory()
        factory.registerBeanDefinition("userController", RootBeanDefinition(UserController::class.java))

        val resolver = CrudServiceDependencyResolver()

        val exception = assertThrows<IllegalArgumentException> {
            resolver.postProcessBeanFactory(factory)
        }

        assertTrue(exception.message!!.contains("no JpaRepository<User, UUID> found"), exception.message)
        assertTrue(exception.message!!.contains("userController"), exception.message)
    }

    @Test
    fun `auto-resolver skips when custom CrudService subclass already covers the type combination`() {
        val factory = DefaultListableBeanFactory()
        // AddressController extends SimpleCrudController<Address, AddressDTO, AddressResponse>
        // AddressServiceImpl extends SimpleCrudService<Address, AddressDTO, AddressResponse>
        // The resolver should detect AddressServiceImpl as an existing impl and skip auto-resolution,
        // even though no AddressRepository is registered.
        factory.registerBeanDefinition("addressController", RootBeanDefinition(AddressController::class.java))
        factory.registerBeanDefinition("addressServiceImpl", RootBeanDefinition(AddressServiceImpl::class.java))

        val resolver = CrudServiceDependencyResolver()

        assertDoesNotThrow {
            resolver.postProcessBeanFactory(factory)
        }
    }

    @RestController
    class ControllerWithoutRequestMapping : SimpleCrudController<User, UserDTO, UserResponse>()

    @Test
    fun `CrudController fails at construction when @RequestMapping annotation is missing`() {
        assertNotNull(
            UserController::class.java.getAnnotation(RequestMapping::class.java),
            "Properly configured controller should have @RequestMapping"
        )

        val exception = assertThrows<IllegalStateException> {
            ControllerWithoutRequestMapping()
        }

        assertTrue(exception.message!!.contains("missing @RequestMapping"), exception.message)
    }
}