package group.phorus.service.dtos.validationGroups

/**
 * Validation group for entity creation via `POST` requests.
 *
 * Marker interface for Jakarta Bean Validation's `groups` feature. Constraints that include
 * `Create::class` in their groups will be enforced when creating new entities.
 *
 * [CrudController][group.phorus.service.controller.CrudController] automatically applies
 * `@Validated(Create::class)` to the `POST` endpoint's request body.
 *
 * Behind the scenes, [CrudService][group.phorus.service.service.CrudService] uses
 * [Phorus Mapper](https://github.com/phorus-group/mapper) to map the DTO to a new entity.
 *
 * ## Example
 *
 * ```kotlin
 * data class UserDTO(
 *     @field:NotBlank(groups = [Create::class, Replace::class], message = "Name is required")
 *     var name: String? = null,
 *
 *     @field:Email(groups = [Create::class, Update::class, Replace::class], message = "Invalid email")
 *     var email: String? = null,
 * )
 * ```
 *
 * In this example:
 * - `POST /user` validates both `name` and `email`
 * - `PATCH /user/{id}` only validates `email` if provided
 * - `PUT /user/{id}` validates both `name` and `email`
 *
 * Typically, create and replace share the same validation rules (both require all mandatory fields),
 * so most constraints use `groups = [Create::class, Replace::class]` together.
 */
interface Create

/**
 * Validation group for partial entity updates via `PATCH` requests.
 *
 * Marker interface for Jakarta Bean Validation's `groups` feature. Constraints that include
 * `Update::class` in their groups will be enforced when partially updating existing entities.
 *
 * [CrudController][group.phorus.service.controller.CrudController] automatically applies
 * `@Validated(Update::class)` to the `PATCH` endpoint's request body.
 *
 * Behind the scenes, [CrudService][group.phorus.service.service.CrudService] uses
 * [Phorus Mapper](https://github.com/phorus-group/mapper) with `UpdateOption.IGNORE_NULLS`,
 * which will skip null fields during the merge onto the existing entity.
 *
 * ## Example
 *
 * ```kotlin
 * data class UserDTO(
 *     @field:NotBlank(groups = [Create::class, Replace::class], message = "Name is required")
 *     var name: String? = null,
 *
 *     @field:Email(groups = [Create::class, Update::class, Replace::class], message = "Invalid email")
 *     var email: String? = null,
 * )
 * ```
 *
 * In this example:
 * - `POST /user` validates both `name` and `email`
 * - `PATCH /user/{id}` only validates `email` if provided
 * - `PUT /user/{id}` validates both `name` and `email`
 */
interface Update

/**
 * Validation group for full entity replacement via `PUT` requests.
 *
 * Marker interface for Jakarta Bean Validation's `groups` feature. Constraints that include
 * `Replace::class` in their groups will be enforced when fully replacing existing entities.
 *
 * [CrudController][group.phorus.service.controller.CrudController] automatically applies
 * `@Validated(Replace::class)` to the `PUT` endpoint's request body.
 *
 * Behind the scenes, [CrudService][group.phorus.service.service.CrudService] uses
 * [Phorus Mapper](https://github.com/phorus-group/mapper) with `UpdateOption.SET_NULLS`,
 * which will explicitly set null values on the entity (overwriting existing data).
 *
 * ## Example
 *
 * ```kotlin
 * data class UserDTO(
 *     @field:NotBlank(groups = [Create::class, Replace::class], message = "Name is required")
 *     var name: String? = null,
 *
 *     @field:Email(groups = [Create::class, Update::class, Replace::class], message = "Invalid email")
 *     var email: String? = null,
 * )
 * ```
 *
 * In this example:
 * - `POST /user` validates both `name` and `email`
 * - `PATCH /user/{id}` only validates `email` if provided
 * - `PUT /user/{id}` validates both `name` and `email`
 *
 * Typically, create and replace share the same validation rules (both require all mandatory fields),
 * so most constraints use `groups = [Create::class, Replace::class]` together.
 */
interface Replace