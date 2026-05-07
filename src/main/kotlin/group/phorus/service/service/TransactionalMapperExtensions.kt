package group.phorus.service.service

import group.phorus.mapper.FunctionMappings
import group.phorus.mapper.Mappings
import group.phorus.mapper.OriginalEntity
import group.phorus.mapper.TargetField
import group.phorus.mapper.mapping.mapTo
import org.springframework.transaction.support.TransactionTemplate
import kotlin.reflect.KType
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.typeOf

/**
 * Maps this entity to a response DTO inside a read-only transaction.
 *
 * [Phorus Mapper](https://github.com/phorus-group/mapper) reflects over every property declared in the target DTO class. If a DTO field maps
 * from a lazy-loaded JPA relationship (like `@OneToMany` collections), Mapper will access that
 * relationship on the entity. Outside a transaction, the Hibernate session is closed and the entity
 * is detached, causing `LazyInitializationException`. This function wraps the mapping in a read-only
 * transaction to keep the session open.
 *
 * Use this when you already have an entity reference (from a repository call or another source)
 * and need to map it to a response DTO. If you need to load an entity from a repository and then
 * map it, use [TransactionTemplate.fetchAndMapTo] instead, which covers both steps in a single
 * transaction.
 *
 * When called inside an existing transaction, it joins that transaction (no new transaction created).
 * When called outside a transaction, it creates a new read-only transaction for the mapping operation.
 * Both cases keep the Hibernate session open long enough for Mapper to traverse all relationships.
 *
 * ```kotlin
 * override suspend fun findWithMetrics(id: UUID): ProductWithMetricsResponse =
 *     withContext(Dispatchers.IO) {
 *         withReadTransaction {
 *             val product = productRepository.findById(id).orElseThrow { NotFound("...") }
 *             val metrics = calculateMetrics(product)
 *             product.transactionalMapTo<ProductWithMetricsResponse>(transactionTemplate)!!
 *         }
 *     }
 * ```
 *
 * Always wrap in `withContext(Dispatchers.IO)` because repository calls and lazy-loading perform
 * blocking I/O, which must not run on the WebFlux event loop.
 *
 * If you prefer to use the original Phorus Mapper [mapTo] directly, you can wrap the call yourself
 * inside a [TransactionTemplate] to achieve the same effect.
 *
 * @param T the target response DTO type (must be known at compile time).
 * @param transactionTemplate the Spring [TransactionTemplate] bean for managing transactions.
 * @param exclusions a list of target fields to exclude from mapping.
 * @param mappings a map of fields to map forcefully, with the format: [OriginalField] - [TargetField] - [MappingFallback].
 * @param functionMappings a map of fields to map forcefully with a mutating function, with the format:
 * [OriginalField] - [MappingFunction] - [TargetField] - [MappingFallback].
 * @param ignoreMapFromAnnotations whether to ignore `@MapFrom` annotations on the target DTO. Default: false.
 * @param useSettersOnly whether to use only setters instead of constructors when building the target. Default: false.
 * @param mapPrimitives whether to map between primitive types (e.g. [String] ↔ [Number]). Default: true.
 * @return the mapped response DTO, or `null` if the entity or Mapper result is `null`.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Any> Any.transactionalMapTo(
    transactionTemplate: TransactionTemplate,
    exclusions: List<TargetField> = emptyList(),
    mappings: Mappings = emptyMap(),
    functionMappings: FunctionMappings = emptyMap(),
    ignoreMapFromAnnotations: Boolean = false,
    useSettersOnly: Boolean = false,
    mapPrimitives: Boolean = true,
): T? {
    val entity = this
    val readOnly = TransactionTemplate(transactionTemplate.transactionManager!!).apply {
        isReadOnly = true
    }
    return readOnly.execute {
        mapTo(
            originalEntity = OriginalEntity(entity, entity::class.starProjectedType),
            targetType = typeOf<T>(),
            exclusions = exclusions,
            mappings = mappings,
            functionMappings = functionMappings,
            ignoreMapFromAnnotations = ignoreMapFromAnnotations,
            useSettersOnly = useSettersOnly,
            mapPrimitives = mapPrimitives,
        ) as T?
    }
}

/**
 * Non-reified overload of [transactionalMapTo] for runtime type resolution.
 *
 * Identical to the reified [transactionalMapTo] but accepts the target type as a [KType] parameter
 * instead of using Kotlin's `reified` mechanism. This is necessary when the target type is only known
 * at runtime, not at compile time.
 *
 * Used internally by [CrudService.findById], which extracts the response type from the class hierarchy
 * at runtime (because type parameters are erased). Most application code should use the reified
 * [transactionalMapTo] or [TransactionTemplate.fetchAndMapTo] instead.
 *
 * ```kotlin
 * val entity = repository.findById(id).orElseThrow { NotFound("...") }
 * return entity.transactionalMapTo<RESPONSE>(responseType, transactionTemplate)!!
 * ```
 *
 * Always wrap in `withContext(Dispatchers.IO)` because repository calls and lazy-loading perform
 * blocking I/O, which must not run on the WebFlux event loop.
 *
 * If you prefer to use the original Phorus Mapper [mapTo] directly, you can wrap the call yourself
 * inside a [TransactionTemplate] to achieve the same effect.
 *
 * @param T the target response type.
 * @param targetType the [KType] representing the target response type, obtained via reflection.
 * @param transactionTemplate the Spring [TransactionTemplate] bean for managing transactions.
 * @param exclusions a list of target fields to exclude from mapping.
 * @param mappings a map of fields to map forcefully, with the format: [OriginalField] - [TargetField] - [MappingFallback].
 * @param functionMappings a map of fields to map forcefully with a mutating function, with the format:
 * [OriginalField] - [MappingFunction] - [TargetField] - [MappingFallback].
 * @param ignoreMapFromAnnotations whether to ignore `@MapFrom` annotations on the target DTO. Default: false.
 * @param useSettersOnly whether to use only setters instead of constructors when building the target. Default: false.
 * @param mapPrimitives whether to map between primitive types (e.g. [String] ↔ [Number]). Default: true.
 * @return the mapped response DTO, or `null` if the entity or Mapper result is `null`.
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any> Any.transactionalMapTo(
    targetType: KType,
    transactionTemplate: TransactionTemplate,
    exclusions: List<TargetField> = emptyList(),
    mappings: Mappings = emptyMap(),
    functionMappings: FunctionMappings = emptyMap(),
    ignoreMapFromAnnotations: Boolean = false,
    useSettersOnly: Boolean = false,
    mapPrimitives: Boolean = true,
): T? {
    val entity = this
    val readOnly = TransactionTemplate(transactionTemplate.transactionManager!!).apply {
        isReadOnly = true
    }
    return readOnly.execute {
        mapTo(
            originalEntity = OriginalEntity(entity, entity::class.starProjectedType),
            targetType = targetType,
            exclusions = exclusions,
            mappings = mappings,
            functionMappings = functionMappings,
            ignoreMapFromAnnotations = ignoreMapFromAnnotations,
            useSettersOnly = useSettersOnly,
            mapPrimitives = mapPrimitives,
        ) as T?
    }
}

/**
 * Loads an entity from a repository and maps it to a response DTO in a single read-only transaction.
 *
 * This is the recommended function for custom service methods that need to fetch an entity and return
 * a mapped response. It handles the complete flow: open transaction, load entity, map to DTO, close
 * transaction. If you already have an entity reference, use [Any.transactionalMapTo] instead.
 *
 * Both the load and the mapping must happen inside the same transaction. If the entity is loaded in one
 * transaction and mapped in another (or outside any transaction), it becomes detached and any lazy-loaded
 * collections will throw `LazyInitializationException` when Mapper tries to access them. This function
 * ensures both operations share the same Hibernate session.
 *
 * Always wrap in `withContext(Dispatchers.IO)` because repository calls and lazy-loading perform
 * blocking I/O, which must not run on the WebFlux event loop.
 *
 * ```kotlin
 * @Service
 * class ProductServiceImpl(
 *     private val productRepository: ProductRepository,
 *     private val transactionTemplate: TransactionTemplate,
 * ) : ProductService() {
 *
 *     override suspend fun findDetailById(id: UUID): ProductDetailResponse =
 *         withContext(Dispatchers.IO) {
 *             transactionTemplate.fetchAndMapTo<ProductDetailResponse> {
 *                 productRepository.findById(id).orElseThrow { NotFound("Product not found") }
 *             }!!
 *         }
 * }
 * ```
 *
 * If you prefer to use the original Phorus Mapper [mapTo] directly, you can wrap both the load and the
 * mapping call yourself inside a [TransactionTemplate] to achieve the same effect.
 *
 * @param T the target response DTO type (must be known at compile time).
 * @param loader a lambda that loads and returns the entity from a repository. This lambda executes inside
 *               the transaction, so the entity remains attached.
 * @param exclusions a list of target fields to exclude from mapping.
 * @param mappings a map of fields to map forcefully, with the format: [OriginalField] - [TargetField] - [MappingFallback].
 * @param functionMappings a map of fields to map forcefully with a mutating function, with the format:
 * [OriginalField] - [MappingFunction] - [TargetField] - [MappingFallback].
 * @param ignoreMapFromAnnotations whether to ignore `@MapFrom` annotations on the target DTO. Default: false.
 * @param useSettersOnly whether to use only setters instead of constructors when building the target. Default: false.
 * @param mapPrimitives whether to map between primitive types (e.g. [String] ↔ [Number]). Default: true.
 * @return the mapped response DTO, or `null` if the loader returns `null` or Mapper returns `null`.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Any> TransactionTemplate.fetchAndMapTo(
    exclusions: List<TargetField> = emptyList(),
    mappings: Mappings = emptyMap(),
    functionMappings: FunctionMappings = emptyMap(),
    ignoreMapFromAnnotations: Boolean = false,
    useSettersOnly: Boolean = false,
    mapPrimitives: Boolean = true,
    crossinline loader: () -> Any,
): T? {
    val readOnly = TransactionTemplate(this.transactionManager!!).apply {
        isReadOnly = true
    }
    return readOnly.execute {
        val entity = loader()
        mapTo(
            originalEntity = OriginalEntity(entity, entity::class.starProjectedType),
            targetType = typeOf<T>(),
            exclusions = exclusions,
            mappings = mappings,
            functionMappings = functionMappings,
            ignoreMapFromAnnotations = ignoreMapFromAnnotations,
            useSettersOnly = useSettersOnly,
            mapPrimitives = mapPrimitives,
        ) as T?
    }
}