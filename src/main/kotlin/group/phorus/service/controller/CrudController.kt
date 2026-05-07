package group.phorus.service.controller

import group.phorus.exception.core.NotFound
import group.phorus.service.dtos.validationGroups.Create
import group.phorus.service.dtos.validationGroups.Replace
import group.phorus.service.dtos.validationGroups.Update
import group.phorus.service.model.BaseEntity
import group.phorus.service.service.CrudService
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.allSupertypes

/**
 * Generic REST controller providing five standard CRUD endpoints for any JPA entity.
 *
 * This class eliminates boilerplate by exposing `GET /{id}`, `POST`, `PATCH /{id}`, `PUT /{id}`,
 * and `DELETE /{id}` endpoints for any entity/DTO combination. You only need to create the controller
 * and a JpaRepository. The matching [CrudService] bean is automatically created at startup and injected as a bean
 * if no bean from a custom implementation is found. If you define your own service implementation (e.g.,
 * `@Service class UserService : SimpleCrudService<...>()`), that bean is used instead.
 * The controller delegates all business logic to the service bean.
 *
 * ## What It Provides
 *
 * Each endpoint maps directly to a [CrudService] method:
 *
 * - `GET /{id}` calls [CrudService.findById], returns 200 OK with the response DTO, or 404 if not found
 * - `POST` calls [CrudService.create], returns 201 Created with a `Location` header pointing to the new resource
 * - `PATCH /{id}` calls [CrudService.update] (partial update, null fields ignored), returns 204 No Content
 * - `PUT /{id}` calls [CrudService.replace] (full replacement, null fields overwrite), returns 204 No Content
 * - `DELETE /{id}` calls [CrudService.delete], returns 204 No Content
 *
 * The service handles all business logic (validation, mapping, transaction management, error handling).
 * This controller is just a thin HTTP layer.
 *
 * ## Validation Groups
 *
 * Each endpoint automatically applies the appropriate Jakarta validation group to the request body:
 *
 * - `POST` uses `@Validated(Create::class)`
 * - `PATCH` uses `@Validated(Update::class)`
 * - `PUT` uses `@Validated(Replace::class)`
 *
 * This lets you use the same DTO class with different validation rules per operation.
 *
 * ## DTO Types Per Operation
 *
 * This class supports separate DTO types for create, update, and replace operations.
 *
 * For the common case where all operations use the same DTO, use the [SimpleCrudController] typealias:
 * ```kotlin
 * @RestController
 * @RequestMapping("/user")
 * class UserController : SimpleCrudController<User, UserDTO, UserResponse>()
 * ```
 *
 * For different DTOs per operation, use the full signature:
 * ```kotlin
 * @RestController
 * @RequestMapping("/user")
 * class UserController : CrudController<User, CreateUserDTO, UpdateUserDTO, ReplaceUserDTO, UserResponse>()
 * ```
 *
 * You can also use a single DTO for more than one operation, just remember to set the validation groups correctly.
 * Using the same DTO for update and replace operations is one of the most common cases:
 * ```kotlin
 * @RestController
 * @RequestMapping("/user")
 * class UserController : CrudController<User, CreateUserDTO, UpdateUserDTO, UpdateUserDTO, UserResponse>()
 * ```
 *
 * ## Adding Custom Endpoints
 *
 * All methods are `open`, allowing subclasses to override existing endpoints or add new ones alongside
 * the inherited CRUD operations:
 *
 * ```kotlin
 * @RestController
 * @RequestMapping("/user")
 * class UserController : SimpleCrudController<User, UserDTO, UserResponse>() {
 *
 *     @GetMapping("/me")
 *     suspend fun currentUser(): UserResponse {
 *         val userId = AuthContext.context.get().userId
 *         return service.findById(userId)
 *     }
 * }
 * ```
 *
 * ## Note that:
 *
 * > The `POST` endpoint returns a `201 Created` response with a `Location` header pointing to the newly
 * > created resource (e.g., `Location: /user/a1b2c3d4-...`). The base path is extracted from the
 * > `@RequestMapping` annotation on the controller class.
 *
 * > Missing a `JpaRepository<ENTITY, UUID>` bean or missing `@RequestMapping` annotation will cause the
 * > application to fail to start.
 *
 * @param ENTITY the JPA entity type, must extend [BaseEntity].
 * @param CREATE_DTO the request body DTO type accepted by `POST`.
 * @param UPDATE_DTO the request body DTO type accepted by `PATCH`.
 * @param REPLACE_DTO the request body DTO type accepted by `PUT`.
 * @param RESPONSE the response DTO type returned by `GET /{id}`, mapped from the entity by [CrudService].
 */
abstract class CrudController<ENTITY: BaseEntity, CREATE_DTO: Any, UPDATE_DTO: Any, REPLACE_DTO: Any, RESPONSE: Any>
    : ApplicationContextAware {

    private val controllerSupertype = this::class.allSupertypes.firstOrNull { it.classifier == CrudController::class }
        ?: error(
            "${this::class.simpleName}: cannot resolve CrudController supertype from class hierarchy. " +
                "Ensure the class extends CrudController or SimpleCrudController with concrete type arguments."
        )

    private val basePath: String = this::class.java.getAnnotation(RequestMapping::class.java)?.value?.firstOrNull()
        ?: error(
            "${this::class.simpleName}: missing @RequestMapping annotation. " +
                "Add @RequestMapping(\"/path\") to the controller class."
        )

    private lateinit var service: CrudService<ENTITY, CREATE_DTO, UPDATE_DTO, REPLACE_DTO, RESPONSE>

    @Suppress("UNCHECKED_CAST")
    override fun setApplicationContext(context: ApplicationContext) {
        val entity = controllerSupertype.arguments[0].type!!.classifier as KClass<*>
        val createDto = controllerSupertype.arguments[1].type!!.classifier as KClass<*>
        val updateDto = controllerSupertype.arguments[2].type!!.classifier as KClass<*>
        val replaceDto = controllerSupertype.arguments[3].type!!.classifier as KClass<*>
        val response = controllerSupertype.arguments[4].type!!.classifier as KClass<*>

        val canonicalName = "crudService-${entity.simpleName}-${createDto.simpleName}-${updateDto.simpleName}-${replaceDto.simpleName}-${response.simpleName}"

        // Try auto-resolved bean by canonical name first
        service = if (context.containsBean(canonicalName)) {
            context.getBean(canonicalName) as CrudService<ENTITY, CREATE_DTO, UPDATE_DTO, REPLACE_DTO, RESPONSE>
        } else {
            // Fall back to custom service subclass matching by type hierarchy
            context.getBeansOfType(CrudService::class.java).values.firstOrNull { service ->
                val serviceType = service::class.allSupertypes.firstOrNull { it.classifier == CrudService::class }
                serviceType != null &&
                    serviceType.arguments[0].type?.classifier == entity &&
                    serviceType.arguments[1].type?.classifier == createDto &&
                    serviceType.arguments[2].type?.classifier == updateDto &&
                    serviceType.arguments[3].type?.classifier == replaceDto &&
                    serviceType.arguments[4].type?.classifier == response
            } as? CrudService<ENTITY, CREATE_DTO, UPDATE_DTO, REPLACE_DTO, RESPONSE>
                ?: error(
                    "${this::class.simpleName}: no matching CrudService<${entity.simpleName}, ${createDto.simpleName}, " +
                        "${updateDto.simpleName}, ${replaceDto.simpleName}, ${response.simpleName}> bean found. " +
                        "Either extend CrudService with a @Service subclass, or ensure a " +
                        "JpaRepository<${entity.simpleName}, UUID> exists for auto-resolution."
                )
        }
    }

    /**
     * Returns the [RESPONSE] for the entity at [id].
     *
     * @param id the [UUID] of the entity to retrieve.
     * @return the mapped [RESPONSE].
     * @throws NotFound if no entity with [id] exists.
     */
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    open suspend fun findById(
        @PathVariable
        id: UUID,
    ): RESPONSE =
        service.findById(id)

    /**
     * Creates an entity from [dto] and returns `201 Created` with a `Location` header
     * pointing to the new resource.
     *
     * @param dto the request body, validated against the [Create] group.
     * @return a `201 Created` response with a `Location` header.
     */
    @PostMapping
    open suspend fun create(
        @Validated(Create::class)
        @RequestBody
        dto: CREATE_DTO,
    ): ResponseEntity<Void> =
        service.create(dto)
            .let { ResponseEntity.created(URI.create("${basePath}/$it")).build() }

    /**
     * Partially updates the entity at [id] by merging [dto]. Only non-null fields
     * in [dto] are applied; null fields are ignored.
     *
     * @param id the [UUID] of the entity to update.
     * @param dto the request body, validated against the [Update] group.
     * @throws NotFound if no entity with [id] exists.
     */
    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    open suspend fun update(
        @PathVariable
        id: UUID,

        @Validated(Update::class)
        @RequestBody
        dto: UPDATE_DTO,
    ) {
        service.update(id, dto)
    }

    /**
     * Fully replaces the entity at [id] with [dto], including null fields.
     *
     * @param id the [UUID] of the entity to replace.
     * @param dto the request body, validated against the [Replace] group.
     * @throws NotFound if no entity with [id] exists.
     */
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    open suspend fun replace(
        @PathVariable
        id: UUID,

        @Validated(Replace::class)
        @RequestBody
        dto: REPLACE_DTO,
    ) {
        service.replace(id, dto)
    }

    /**
     * Deletes the entity at [id].
     *
     * @param id the [UUID] of the entity to delete.
     * @throws NotFound if no entity with [id] exists.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    open suspend fun delete(@PathVariable id: UUID) {
        service.delete(id)
    }
}

/**
 * Convenience typealias for [CrudController] when the same DTO type is used for create, update, and replace operations.
 *
 * This is the common case where all operations share the same DTO structure, differentiated only by
 * validation groups.
 *
 * **Example:**
 * ```kotlin
 * @RestController
 * @RequestMapping("/user")
 * class UserController : SimpleCrudController<User, UserDTO, UserResponse>()
 * ```
 *
 * For cases where operations need different DTO structures (e.g., you need password property on create but not update),
 * use the full [CrudController] signature with 5 type parameters.
 */
typealias SimpleCrudController<ENTITY, DTO, RESPONSE> = CrudController<ENTITY, DTO, DTO, DTO, RESPONSE>