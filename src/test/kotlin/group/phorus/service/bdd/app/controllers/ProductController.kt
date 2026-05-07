package group.phorus.service.bdd.app.controllers

import group.phorus.service.bdd.app.dtos.ProductDTO
import group.phorus.service.bdd.app.dtos.ProductResponse
import group.phorus.service.bdd.app.model.Product
import group.phorus.service.controller.SimpleCrudController
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/product")
class ProductController : SimpleCrudController<Product, ProductDTO, ProductResponse>()