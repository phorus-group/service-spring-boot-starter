package group.phorus.service.service

import group.phorus.exception.core.NotFound
import group.phorus.mapper.FunctionMappings
import group.phorus.mapper.OriginalEntity
import group.phorus.mapper.mapping.MappingFallback
import group.phorus.mapper.mapping.UpdateOption
import group.phorus.mapper.mapping.mapTo
import group.phorus.service.controller.CrudController
import group.phorus.service.model.BaseEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.BeanNameAware
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.getBean
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.ResolvableType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.support.TransactionTemplate
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaField

private fun JpaRepository<*, *>.getRepositoryEntity(): KClass<*> {
    // Get the interface that is extending this JpaRepository to avoid type erasure
    val inter = this.javaClass.genericInterfaces.first() as Class<*>

    // Get the entity class from the first value parameter of the delete function
    return inter.kotlin.members.first { it.name == "delete" }
        .valueParameters.first().type.classifier as KClass<*>
}

@Suppress("UNCHECKED_CAST")
private fun ListableBeanFactory.getRepositoriesMap() = this.getBeansOfType(JpaRepository::class.java).map { (_, value) ->
    value.getRepositoryEntity() to value
}.toMap() as Map<KClass<*>, JpaRepository<*, UUID>>

/**
 * Spring [BeanFactoryPostProcessor] that automatically creates [CrudService] beans when needed.
 *
 * This resolver runs at application startup before any beans are instantiated. It works in two phases:
 * first it discovers where [CrudService] instances are needed, then it creates bean definitions for
 * those that don't already have custom implementations. Without this resolver, every controller would
 * need a matching service implementation manually created, even if that service contains zero custom
 * logic. This resolver eliminates that boilerplate by auto-creating generic `CrudService` beans when
 * no bean from a custom implementation is found.
 *
 * ## Phase 1: Discovery
 *
 * The resolver scans all bean definitions to find where `CrudService` instances are needed. It checks
 * two places:
 *
 * **Source 1: Constructor Parameters**
 *
 * Any bean constructor that requests `CrudService<Entity, CreateDTO, UpdateDTO, ReplaceDTO, Response>`
 * as a parameter triggers auto-resolution for that type combination.
 *
 * Example:
 * ```kotlin
 * class SomeBean(private val service: SimpleCrudService<User, UserDTO, UserResponse>)
 * ```
 * creates a requirement for `CrudService<User, UserDTO, UserDTO, UserDTO, UserResponse>`.
 *
 * **Source 2: CrudController Subclasses**
 *
 * Any controller extending `CrudController<Entity, CreateDTO, UpdateDTO, ReplaceDTO, Response>` implies
 * it needs a matching `CrudService` bean to delegate to.
 *
 * Example:
 * ```kotlin
 * @RestController
 * @RequestMapping("/user")
 * class UserController : SimpleCrudController<User, UserDTO, UserResponse>()
 * ```
 * creates a requirement for `CrudService<User, UserDTO, UserDTO, UserDTO, UserResponse>`.
 *
 * ## Phase 2: Registration
 *
 * For each unique Entity/CreateDTO/UpdateDTO/ReplaceDTO/Response combination discovered in Phase 1:
 *
 * **Step 1: Check if a bean from a custom implementation already exists**
 *
 * The resolver scans for existing `CrudService` beans with matching type parameters (e.g., a bean
 * such as `@Service class UserService : SimpleCrudService<User, UserDTO, UserResponse>()`). If found,
 * it skips that type combination entirely because the developer has provided their own implementation.
 *
 * **Step 2: Find the matching JpaRepository**
 *
 * The resolver searches for a `JpaRepository<Entity, UUID>` bean in the Spring context. If no repository
 * is found, the application will fail to start.
 *
 * **Step 3: Create a new CrudService bean definition**
 *
 * The resolver registers a `RootBeanDefinition` for the generic `CrudService` class with the correct
 * type parameters set via `setTargetType()`. This bean definition is registered with a canonical name
 * like `"crudService-User-UserDTO-UserDTO-UserDTO-UserResponse"`.
 *
 * **Step 4: Store metadata as bean definition attributes**
 *
 * Instead of passing constructor parameters (which would require visible constructor args on `CrudService`),
 * the resolver stores metadata directly in the bean definition as attributes:
 *
 * - `crudService.repositoryBeanName`: the Spring bean name of the matching `JpaRepository`
 * - `crudService.responseClass`: the Java `Class<Response>` for entity-to-DTO mapping
 *
 * These attributes are invisible to the bean instance during construction but can be read back later
 * via the bean factory. This keeps the `CrudService` class signature clean with zero visible constructor
 * parameters for user-defined subclasses.
 *
 * ## Runtime: How CrudService Reads Its Metadata
 *
 * When Spring instantiates an auto-resolved `CrudService` bean later at runtime:
 *
 * 1. Spring calls `setBeanName(name)` via [BeanNameAware], giving the bean its own bean name
 * 2. Spring calls `setApplicationContext(ctx)` via [ApplicationContextAware]
 * 3. On the first method call, the `CrudService` lazily reads its bean definition attributes via
 *    `context.beanFactory.getBeanDefinition(selfBeanName).getAttribute(...)` to retrieve the repository
 *    bean name and response class that were stored during Phase 2 Step 4
 * 4. The bean then calls `context.getBean(repositoryBeanName)` to obtain its repository instance
 *
 * This attribute-based communication happens entirely through Spring's bean factory infrastructure and
 * is invisible to developers using the library.
 */
@AutoConfiguration
class CrudServiceDependencyResolver : BeanFactoryPostProcessor {
    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
        val beanNames = beanFactory.beanDefinitionNames

        val existingCrudServiceImpls = beanFactory.getBeanNamesForType(CrudService::class.java)
            .mapNotNull { beanName -> beanFactory.getBeanDefinition(beanName).beanClassName?.let { Class.forName(it).kotlin } }
            .map { clazz ->
                val supertype = clazz.allSupertypes.first { it.classifier == CrudService::class }
                val entity = supertype.arguments[0].type!!.classifier as KClass<*>
                val createDto = supertype.arguments[1].type!!.classifier as KClass<*>
                val updateDto = supertype.arguments[2].type!!.classifier as KClass<*>
                val replaceDto = supertype.arguments[3].type!!.classifier as KClass<*>
                val response = supertype.arguments[4].type!!.classifier as KClass<*>

                "crudService-${entity.simpleName}-${createDto.simpleName}-${updateDto.simpleName}-${replaceDto.simpleName}-${response.simpleName}"
            }

        // Collect all required CrudService type combinations from two sources
        data class CrudServiceRequest(
            val entity: KClass<*>,
            val createDto: KClass<*>,
            val updateDto: KClass<*>,
            val replaceDto: KClass<*>,
            val response: KClass<*>,
            val dependentBeanName: String,
        )

        val requests = mutableListOf<CrudServiceRequest>()

        for (beanName in beanNames) {
            val bean = beanFactory.getBeanDefinition(beanName)
            val clazz = bean.beanClassName?.let { Class.forName(it) } ?: continue
            val kClass = clazz.kotlin

            // Source 1: Constructor parameters requesting CrudService<...>
            val constructor = kClass.constructors.firstOrNull()
            if (constructor != null) {
                val crudServiceParams = constructor.parameters.map { it.type }.filter { it.classifier == CrudService::class }
                crudServiceParams.forEach { crudServiceType ->
                    requests.add(CrudServiceRequest(
                        entity = crudServiceType.arguments[0].type!!.classifier as KClass<*>,
                        createDto = crudServiceType.arguments[1].type!!.classifier as KClass<*>,
                        updateDto = crudServiceType.arguments[2].type!!.classifier as KClass<*>,
                        replaceDto = crudServiceType.arguments[3].type!!.classifier as KClass<*>,
                        response = crudServiceType.arguments[4].type!!.classifier as KClass<*>,
                        dependentBeanName = beanName,
                    ))
                }
            }

            // Source 2: CrudController subclasses (type params imply a CrudService is needed)
            val controllerSupertype = kClass.allSupertypes.firstOrNull { it.classifier == CrudController::class }
            if (controllerSupertype != null && controllerSupertype.arguments.size == 5) {
                requests.add(CrudServiceRequest(
                    entity = controllerSupertype.arguments[0].type!!.classifier as KClass<*>,
                    createDto = controllerSupertype.arguments[1].type!!.classifier as KClass<*>,
                    updateDto = controllerSupertype.arguments[2].type!!.classifier as KClass<*>,
                    replaceDto = controllerSupertype.arguments[3].type!!.classifier as KClass<*>,
                    response = controllerSupertype.arguments[4].type!!.classifier as KClass<*>,
                    dependentBeanName = beanName,
                ))
            }
        }

        // Create a bean for every unique combination, if needed
        requests.forEach { (entity, createDto, updateDto, replaceDto, response, dependentBeanName) ->
            val crudBeanName = "crudService-${entity.simpleName}-${createDto.simpleName}-${updateDto.simpleName}-${replaceDto.simpleName}-${response.simpleName}"

                // Skip if the bean already exists
                if (beanFactory.containsBean(crudBeanName) || existingCrudServiceImpls.contains(crudBeanName))
                    return@forEach

                // Get the matching repository definition
                val repositoryNames = beanFactory.getBeanNamesForType(
                    ResolvableType.forClassWithGenerics(
                        JpaRepository::class.java,
                        entity.java,
                        UUID::class.java,
                    )
                )
                require(repositoryNames.isNotEmpty()) {
                    "CrudService auto-resolution failed for bean '$dependentBeanName': " +
                        "no JpaRepository<${entity.simpleName}, UUID> found in the application context. " +
                        "Define a repository interface, e.g.: interface ${entity.simpleName}Repository : JpaRepository<${entity.simpleName}, UUID>"
                }
                val repositoryBeanName = repositoryNames.first()

                // Create the new CrudService bean definition for this entity and DTOs
                val bd = RootBeanDefinition(CrudService::class.java).apply {
                    scope = ConfigurableBeanFactory.SCOPE_PROTOTYPE
                    setTargetType(ResolvableType.forClassWithGenerics(
                        CrudService::class.java,
                        entity.java,
                        createDto.java,
                        updateDto.java,
                        replaceDto.java,
                        response.java,
                    ))
                    // Store metadata as bean definition attributes; CrudService reads them
                    // via BeanNameAware at runtime to resolve its repository and response type
                    setAttribute("crudService.repositoryBeanName", repositoryBeanName)
                    setAttribute("crudService.responseClass", response.java)
                }
                // Register the new CrudService bean definition
                (beanFactory as DefaultListableBeanFactory).registerBeanDefinition(crudBeanName, bd)

                // Register the new bean as a dependency
                beanFactory.registerDependentBean(crudBeanName, dependentBeanName)
        }
    }
}

/**
 * Generic service providing five standard CRUD operations for any JPA entity.
 *
 * This class eliminates boilerplate by providing `findById`, `create`, `update`, `replace`, and `delete`
 * operations for any entity/DTO combination. Controllers and other services can use this directly
 * via auto-resolution (autowiring it as any other bean), or extend it to add custom business logic.
 *
 * Each operation handles the complete flow from DTO to entity to database and back:
 *
 * - **[findById]**: loads the entity by [UUID], maps it to a [RESPONSE], returns the response
 * - **[create]**: maps the [CREATE_DTO] to a new entity, saves it, returns the generated [UUID]
 * - **[update]**: loads the existing entity, merges the [UPDATE_DTO] onto it (null fields ignored), saves it
 * - **[replace]**: loads the existing entity, overwrites all fields from the [REPLACE_DTO] (nulls included), saves it
 * - **[delete]**: loads the entity by [UUID], deletes it
 *
 * All object conversions (DTO to entity, entity to response) use [Phorus Mapper](https://github.com/phorus-group/mapper).
 *
 * **Relationship mapping with [MapTo] and `@MapFrom`:**
 *
 * - **On writes** ([create], [update], [replace]): Fields annotated with [MapTo] in the DTO are resolved from [UUID]
 *   to full entity. For example, a DTO field `authorId: UUID` annotated with `@MapTo("author")` will be looked up via
 *   `AuthorRepository.findById(authorId)`, and the resulting `Author` entity is set on the target entity's `author` field.
 *
 * - **On reads** ([findById]): Fields annotated with `@MapFrom` in the [RESPONSE] extract nested values from
 *   entity relationships back to flat fields. For example, a response field `authorId: UUID` annotated with
 *   `@MapFrom(["author/id"])` will extract `entity.author.id` and populate `response.authorId`.
 *
 * This bidirectional mapping keeps DTOs flat while entities maintain proper JPA relationships.
 * Services return DTOs instead of entities to avoid exposing lazy-loading proxies and Hibernate sessions
 * to controllers and other consumers.
 *
 * ## DTO Types Per Operation
 *
 * This class supports separate DTO types for [create], [update], and [replace] operations. This lets you
 * require different fields depending on the operation (e.g., a password field required on [create] but not on [update]).
 *
 * For the common case where all operations use the same DTO, use the [SimpleCrudService] typealias:
 * ```kotlin
 * abstract class UserService : SimpleCrudService<User, UserDTO, UserResponse>()
 * ```
 *
 * For different DTOs per operation, use the full signature:
 * ```kotlin
 * abstract class UserService : CrudService<User, CreateUserDTO, UpdateUserDTO, UpdateUserDTO, UserResponse>()
 * ```
 *
 * ## Extending for Custom Logic
 *
 * All methods are `open`, allowing subclasses to override individual operations to add authorization,
 * validation, or any other custom logic while still delegating to `super` for the base behavior:
 *
 * ```kotlin
 * @Service
 * class EmployeeServiceImpl(
 *     private val employeeRepository: EmployeeRepository,
 * ) : EmployeeService() {
 *     override suspend fun create(dto: EmployeeDTO): UUID {
 *         // Custom pre-validation
 *         val exists = withContext(Dispatchers.IO) {
 *             employeeRepository.existsByEmail(dto.email!!)
 *         }
 *         if (exists) throw Conflict("Email already exists")
 *
 *         // Delegate to base implementation
 *         return super.create(dto)
 *     }
 * }
 * ```
 *
 * Subclasses need no constructor parameters. All dependencies (repository, transaction template, etc.) are
 * resolved automatically at runtime.
 *
 * ## How Dependencies Are Resolved
 *
 * Dependencies are resolved automatically via one of two paths depending on how the bean was created.
 *
 * ### Path 1: Auto-Resolved Beans
 *
 * When [CrudServiceDependencyResolver] creates a generic `CrudService` bean at startup (because a controller
 * or other bean requested it), the type parameters are erased at runtime due to Java's type erasure. To work
 * around this, the resolver stores metadata in the bean definition as attributes before the bean is instantiated:
 *
 * - `crudService.repositoryBeanName`: the Spring bean name of the matching `JpaRepository`
 * - `crudService.responseClass`: the Java `Class<?>` for the response type
 *
 * During bean initialization, [setApplicationContext] reads those attributes back via [BeanNameAware]
 * (to know its own bean name) and [ApplicationContextAware] (to access the bean factory). The [resolveMetadata]
 * function returns these values, and [repository] and [responseType] are resolved from them eagerly.
 *
 * ### Path 2: User-Defined Subclasses
 *
 * When a developer creates their own service class (e.g., `@Service class UserService : SimpleCrudService<...>()`),
 * Spring's component scan registers it normally. [CrudServiceDependencyResolver] detects this existing bean
 * and skips it entirely, so no attributes are set.
 *
 * During bean initialization, [resolveMetadata] returns `null` because no attributes exist. At that point, the
 * resolution falls back to reflection:
 *
 * - `crudSupertype` finds `CrudService` in the class hierarchy via `this::class.allSupertypes`
 * - `entityClass` extracts the `ENTITY` type parameter from the supertype
 * - `repository` looks up `JpaRepository<ENTITY, UUID>` from the Spring context
 * - `responseType` extracts the `RESPONSE` type parameter from the supertype
 *
 * Both paths achieve the same result (a working repository and response type) but use different mechanisms
 * to avoid the type erasure problem. All resolution happens eagerly at startup, so misconfigurations
 * will fail fast and cause the application to fail at startup.
 *
 * ## Transaction Management
 *
 * All operations run inside explicit transactions via [TransactionTemplate]:
 *
 * - **[findById]** uses a read-only transaction, keeping the Hibernate session open during entity-to-DTO mapping
 *   so that lazy-loaded collections (`@OneToMany`, `@ManyToMany`) can be traversed safely
 * - **[create], [update], [replace], [delete]** use read-write transactions for database modifications
 *
 * [Phorus Mapper](https://github.com/phorus-group/mapper) uses reflection to access entity properties during
 * DTO mapping. When it accesses a lazy-loaded relationship (like `@OneToMany` collections or `@ManyToOne` proxies),
 * Hibernate needs an active session to fetch that data from the database. Without an active session, accessing
 * these properties throws `LazyInitializationException`.
 *
 * The common workaround is setting `enable_lazy_load_no_trans: true`, which opens a new temporary connection
 * for each lazy property access. This is extremely inefficient: if you access 5 lazy relationships, Hibernate opens
 * and closes a database connection 5 times.
 *
 * By wrapping operations in transactions, the session stays open throughout the entire operation, allowing
 * Mapper to safely access all entity properties and resolve [MapTo] annotations using a single database connection.
 * Lazy loading may still trigger multiple queries (N+1), and the developer needs to take care of that on their
 * own, but they all use the same connection instead of opening a new one for each access.
 *
 * @param ENTITY the JPA entity type, must extend [BaseEntity] (provides [UUID]primary key).
 * @param CREATE_DTO the DTO type accepted by [create].
 * @param UPDATE_DTO the DTO type accepted by [update] (partial updates, null fields ignored).
 * @param REPLACE_DTO the DTO type accepted by [replace] (full replacement, null fields overwrite).
 * @param RESPONSE the response DTO type returned by [findById], mapped from the entity.
 */
open class CrudService<ENTITY: BaseEntity, CREATE_DTO: Any, UPDATE_DTO: Any, REPLACE_DTO: Any, RESPONSE: Any>
    : ApplicationContextAware, BeanNameAware {

    private lateinit var selfBeanName: String

    @Suppress("UNCHECKED_CAST")
    private lateinit var repositories: Map<KClass<*>, JpaRepository<*, UUID>>
    private lateinit var transactionTemplate: TransactionTemplate
    private lateinit var entityClass: KClass<*>
    private lateinit var repository: JpaRepository<ENTITY, UUID>
    private lateinit var responseType: KType

    override fun setBeanName(name: String) {
        selfBeanName = name
    }

    @Suppress("UNCHECKED_CAST")
    override fun setApplicationContext(context: ApplicationContext) {
        val autoResolvedMetadata = resolveMetadata(context)

        // For user-defined subclasses (Path 2), resolve type parameters via reflection on the class hierarchy.
        // Auto-resolved beans skip this because this::class == CrudService::class (no concrete supertype to inspect).
        val crudSupertype = if (autoResolvedMetadata == null) {
            this::class.allSupertypes.firstOrNull { it.classifier == CrudService::class }
                ?: error(
                    "${this::class.simpleName}: cannot resolve CrudService supertype from class hierarchy. " +
                        "Ensure the class extends CrudService or SimpleCrudService with concrete type arguments."
                )
        } else null

        repositories = context.getRepositoriesMap()

        transactionTemplate = runCatching { context.getBean<TransactionTemplate>() }.getOrElse {
            error(
                "${this::class.simpleName}: no TransactionTemplate bean found in the application context. " +
                    "Ensure spring-boot-starter-data-jpa is on the classpath and JPA is configured."
            )
        }

        entityClass = autoResolvedMetadata?.let { (repoName, _) ->
            (context.getBean(repoName) as JpaRepository<*, *>).getRepositoryEntity()
        } ?: (crudSupertype!!.arguments[0].type?.classifier as? KClass<*>
            ?: error(
                "${this::class.simpleName}: cannot resolve ENTITY type parameter from class hierarchy. " +
                    "Ensure the class extends CrudService/SimpleCrudService with concrete type arguments, " +
                    "e.g.: class MyService : SimpleCrudService<MyEntity, MyDTO, MyResponse>()"
            ))

        repository = autoResolvedMetadata?.let { (repoName, _) ->
            context.getBean(repoName) as JpaRepository<ENTITY, UUID>
        } ?: (repositories[entityClass] as? JpaRepository<ENTITY, UUID>
            ?: error(
                "${this::class.simpleName}: no JpaRepository<${entityClass.simpleName}, UUID> found in the application context. " +
                    "Define a repository interface, e.g.: interface ${entityClass.simpleName}Repository : JpaRepository<${entityClass.simpleName}, UUID>"
            ))

        responseType = autoResolvedMetadata?.let { (_, respClass) ->
            respClass.kotlin.createType()
        } ?: (crudSupertype!!.arguments[4].type
            ?: error(
                "${this::class.simpleName}: cannot resolve RESPONSE type parameter from class hierarchy. " +
                    "Ensure the class extends CrudService/SimpleCrudService with concrete type arguments."
            ))
    }

    /**
     * For auto-resolved beans, reads repository bean name and response class from bean definition
     * attributes set by [CrudServiceDependencyResolver]. Returns `null` for user-defined subclasses.
     */
    private fun resolveMetadata(context: ApplicationContext): Pair<String, Class<*>>? {
        val factory = (context as? ConfigurableApplicationContext)?.beanFactory ?: return null
        if (!factory.containsBeanDefinition(selfBeanName)) return null
        val bd = factory.getBeanDefinition(selfBeanName)
        val repoName = bd.getAttribute("crudService.repositoryBeanName") as? String ?: return null
        val respClass = bd.getAttribute("crudService.responseClass") as? Class<*> ?: return null
        return repoName to respClass
    }

    /**
     * Finds an entity by [id] and returns its [RESPONSE] representation.
     *
     * The load and mapping run inside a single read-only transaction so that
     * Mapper can traverse lazy-loaded relationships.
     *
     * @param id the [UUID] of the entity to retrieve.
     * @return the mapped [RESPONSE].
     * @throws NotFound if no entity with [id] exists.
     */
    open suspend fun findById(id: UUID): RESPONSE =
        withContext(Dispatchers.IO) {
            withReadTransaction {
                repository.findById(id)
                    .orElseThrow { NotFound("${entityClass.simpleName} with id $id not found.") }
                    .transactionalMapTo(responseType, transactionTemplate)!!
            }
        }

    /**
     * Converts [dto] to an entity via Mapper (resolving [MapTo] relationships),
     * saves it, and returns the generated [UUID].
     *
     * @param dto the data transfer object to convert and persist.
     * @return the generated [UUID] of the new entity.
     */
    @Suppress("UNCHECKED_CAST")
    open suspend fun create(dto: CREATE_DTO): UUID =
        withContext(Dispatchers.IO) {
            withTransaction {
                val entity = mapTo(
                    originalEntity = OriginalEntity(dto, dto::class.starProjectedType),
                    targetType = entityClass.createType(),
                    functionMappings = getFunctionMappings(dto),
                ) as ENTITY
                repository.save(entity).id!!
            }
        }

    /**
     * Partially updates the entity at [id] by merging [dto] with Mapper's `IGNORE_NULLS`.
     * Only non-null fields in [dto] are applied; null fields are ignored.
     *
     * @param id the [UUID] of the entity to update.
     * @param dto the data transfer object containing the fields to merge.
     * @throws NotFound if no entity with [id] exists.
     */
    @Suppress("UNCHECKED_CAST")
    open suspend fun update(id: UUID, dto: UPDATE_DTO) {
        withContext(Dispatchers.IO) {
            withTransaction {
                val entity = repository.findById(id)
                    .orElseThrow { NotFound("${entityClass.simpleName} with id $id not found.") }
                val updatedEntity = mapTo(
                    originalEntity = OriginalEntity(dto, dto::class.starProjectedType),
                    targetType = entityClass.createType(),
                    baseEntity = entity to UpdateOption.IGNORE_NULLS,
                    useSettersOnly = true,
                    functionMappings = getFunctionMappings(dto),
                ) as ENTITY
                repository.save(updatedEntity)
            }
        }
    }

    /**
     * Fully replaces the entity at [id] with [dto], including null fields.
     * Null fields in [dto] overwrite existing values in the entity.
     *
     * @param id the [UUID] of the entity to replace.
     * @param dto the data transfer object containing all fields.
     * @throws NotFound if no entity with [id] exists.
     */
    @Suppress("UNCHECKED_CAST")
    open suspend fun replace(id: UUID, dto: REPLACE_DTO) {
        withContext(Dispatchers.IO) {
            withTransaction {
                val entity = repository.findById(id)
                    .orElseThrow { NotFound("${entityClass.simpleName} with id $id not found.") }
                val replacedEntity = mapTo(
                    originalEntity = OriginalEntity(dto, dto::class.starProjectedType),
                    targetType = entityClass.createType(),
                    baseEntity = entity to UpdateOption.SET_NULLS,
                    useSettersOnly = true,
                    functionMappings = getFunctionMappings(dto),
                ) as ENTITY
                repository.save(replacedEntity)
            }
        }
    }

    /**
     * Finds and deletes the entity at [id].
     *
     * @param id the [UUID] of the entity to delete.
     * @throws NotFound if no entity with [id] exists.
     */
    open suspend fun delete(id: UUID) {
        withContext(Dispatchers.IO) {
            withTransaction {
                val entity = repository.findById(id)
                    .orElseThrow { NotFound("${entityClass.simpleName} with id $id not found.") }
                repository.delete(entity)
            }
        }
    }

    /**
     * Runs [block] inside a read-write transaction.
     *
     * @param T the return type of [block].
     * @param block the operation to execute within the transaction.
     * @return the result of [block].
     */
    protected fun <T> withTransaction(block: () -> T): T =
        transactionTemplate.execute { block() }!!

    /**
     * Runs [block] inside a read-only transaction.
     *
     * @param T the return type of [block].
     * @param block the operation to execute within the read-only transaction.
     * @return the result of [block].
     */
    protected fun <T> withReadTransaction(block: () -> T): T {
        val readOnly = TransactionTemplate(transactionTemplate.transactionManager!!).apply {
            isReadOnly = true
        }
        return readOnly.execute { block() }!!
    }

    private fun <DTO: Any> getFunctionMappings(dto: DTO): FunctionMappings {
        val mappings = mutableMapOf<String, Pair<String, Pair<KClass<*>, String>>>()

        // Find fields in the DTO containing a MapTo annotation
        dto::class.memberProperties.forEach { property ->
            val annotations = property.javaField?.annotations?.filterIsInstance<MapTo>()
            annotations?.forEach { annotation ->
                // Extract the KClass of the target fields and save the DTO sourceField, entity targetField, and target KClass
                val targetField = annotation.field
                val function = annotation.function
                val entity = entityClass.memberProperties.first { it.name == targetField }.returnType.classifier as KClass<*>

                mappings[property.name] = targetField to (entity to function)
            }
        }

        return mappings.mapNotNull { (sourceField, target) ->
            val (targetField, targetEntity) = target
            val (targetKClass, function) = targetEntity

            @Suppress("UNCHECKED_CAST")
            val repository = repositories[targetKClass]?.let {
                it as JpaRepository<*, Any>
            } ?: return@mapNotNull null
            val repositoryFunction = repository::class.memberFunctions.firstOrNull {
                it.name == function
            } ?: return@mapNotNull null

            val fetchRelation : (Any) -> Any = { value ->
                val response = repositoryFunction.call(repository, value) as Optional<*>
                response.orElseThrow {
                    NotFound("${targetField.replaceFirstChar { it.uppercase() }} with $sourceField $value not found.")
                }
            }

            // If the user is calling an update without the id field (so the id field is null), we want to
            // continue as a fallback to make the final entity keep the current entities
            // If the id field is set, then we want to allow throwing in case the entity is not found in the repository
            val fallback = if (dto::class.memberProperties.find { it.name == sourceField }?.getter?.call(dto) == null) {
                MappingFallback.CONTINUE
            } else MappingFallback.NULL_OR_THROW

            sourceField to (fetchRelation to (targetField to fallback))
        }.toMap()
    }
}

/**
 * Convenience typealias for [CrudService] when the same DTO type is used for create, update, and replace operations.
 *
 * This is the common case where all operations share the same DTO structure, differentiated only by
 * validation groups.
 *
 * **Example:**
 * ```kotlin
 * abstract class UserService : SimpleCrudService<User, UserDTO, UserResponse>()
 * ```
 *
 * For cases where operations need different DTO structures (e.g., a password field required on create but not update),
 * use the full [CrudService] signature with 5 type parameters.
 */
typealias SimpleCrudService<ENTITY, DTO, RESPONSE> = CrudService<ENTITY, DTO, DTO, DTO, RESPONSE>

/**
 * Marks a DTO field as a foreign key [UUID] that should be resolved to a full entity relationship during writes.
 *
 * Use this annotation on DTO fields that contain a [UUID] reference to another entity. During [CrudService.create],
 * [CrudService.update], and [CrudService.replace], the annotated field's [UUID] value is looked up via the target
 * entity's [JpaRepository], and the resolved entity is set on the target entity's relationship field.
 *
 * **Example - Book entity with an Author relationship:**
 *
 * ```kotlin
 * // Entity with relationship
 * @Entity
 * class Book(
 *     @ManyToOne
 *     var author: Author? = null,  // JPA relationship field
 *
 *     var title: String? = null,
 * ) : BaseEntity()
 *
 * // DTO with UUID field
 * data class BookDTO(
 *     @MapTo("author")  // Resolves authorId -> Author entity via AuthorRepository.findById
 *     var authorId: UUID? = null,
 *
 *     var title: String? = null,
 * )
 *
 * // Response DTO extracts UUID back out
 * data class BookResponse(
 *     var id: UUID? = null,
 *
 *     @MapFrom(["author/id"])  // Extracts book.author.id -> authorId
 *     var authorId: UUID? = null,
 *
 *     var title: String? = null,
 * )
 * ```
 *
 * **Flow:**
 * - **POST/PATCH/PUT**: `BookDTO { authorId = <uuid> }` -> `@MapTo` calls `AuthorRepository.findById(<uuid>)`
 *   -> resulting `Author` entity is set as `Book.author`
 * - **GET**: `Book { author = Author(...) }` -> `@MapFrom(["author/id"])` extracts `book.author.id`
 *   -> populates `BookResponse.authorId`
 *
 * This keeps DTOs flat (only UUIDs) while entities maintain proper JPA relationships (actual entity references).
 *
 * If the repository lookup fails (entity not found), [CrudService] will throw [NotFound] with a descriptive message.
 * On partial updates (PATCH), if the annotated field is `null` in the DTO, Mapper's fallback keeps the existing
 * relationship, so you can update other fields without touching the relationship.
 *
 * @param field the name of the target entity field to populate (e.g., `"author"` maps to the `author: Author` field on the entity).
 * @param function the repository method to call for resolution, defaults to `"findById"`. The method must accept the annotated
 *                 field's type (typically [UUID]) as a parameter and return `Optional<Entity>`.
 */
@Target(AnnotationTarget.FIELD)
@MustBeDocumented
annotation class MapTo(
    val field: String,
    val function: String = "findById",
)