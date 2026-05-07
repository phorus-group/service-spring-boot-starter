Feature: Custom CrudService override
  When a @Service subclass overrides a CrudService method, an empty CrudController should
  automatically discover that service (instead of an auto-resolved generic bean) and use
  the overridden behavior.

  Scenario: Empty controller uses custom service that uppercases the name on create
    Given the caller has the given Product:
      | name        |
      | testProduct |
    When the POST "/product" endpoint is called
    Then the service returns HTTP 201
    And the Product was created with an uppercased name
