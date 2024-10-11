package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Autowired
    private IFollowService followService;

    /**
     * 关注和取关用户
     * @param followUserId
     * @param isFollow
     * @return
     */
    @PutMapping("/{id}/{isFollow}")
    private Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    /**
     * 展示关注和取关状态
     * @param followUserId
     * @return
     */
    @GetMapping("/or/not/{id}")
    private Result isFollow(@PathVariable("id") Long followUserId) {
        return followService.isfollow(followUserId);
    }

    /**
     * 根据目标用户id查询与当前用户的共同关注
     * @param id
     * @return
     */
    @GetMapping("/common/{id}")
    private Result followCommons(@PathVariable("id") Long id) {
        return followService.followCommons(id);
    }

}
