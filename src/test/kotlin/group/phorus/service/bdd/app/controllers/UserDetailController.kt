package group.phorus.service.bdd.app.controllers

import group.phorus.service.bdd.app.dtos.UserDTO
import group.phorus.service.bdd.app.dtos.UserDetailResponse
import group.phorus.service.bdd.app.model.User
import group.phorus.service.controller.SimpleCrudController
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/user-detail")
class UserDetailController : SimpleCrudController<User, UserDTO, UserDetailResponse>()