package co.yiiu.pybbs.controllers;

import co.yiiu.pybbs.conf.properties.SiteConfig;
import co.yiiu.pybbs.exceptions.ApiAssert;
import co.yiiu.pybbs.models.Comment;
import co.yiiu.pybbs.models.Topic;
import co.yiiu.pybbs.models.User;
import co.yiiu.pybbs.services.CommentService;
import co.yiiu.pybbs.services.NotificationService;
import co.yiiu.pybbs.services.TopicService;
import co.yiiu.pybbs.services.UserService;
import co.yiiu.pybbs.utils.Result;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@RestController
@RequestMapping("/comment")
public class CommentController extends BaseController {

  @Autowired
  private CommentService commentService;
  @Autowired
  private TopicService topicService;
  @Autowired
  private SiteConfig siteConfig;
  @Autowired
  private UserService userService;
  @Autowired
  private NotificationService notificationService;

  @PostMapping("/create")
  public Result create(String topicId, String content, String commentId) {
    User user = getUser();
    ApiAssert.notEmpty(topicId, "要评论的话题ID不能为空");
    Topic topic = topicService.findById(topicId);
    ApiAssert.notNull(topic, "要评论的话题不存在");
    ApiAssert.notEmpty(content, "评论的内容不能为空");
    // 将内容里的 \n 转成 <br/>
    content = content.replaceAll("\\n", "<br/>");
    // 声明通知用到的两个变量
    String targetUserId = topic.getUserId();
    String action = "COMMENT";
    // 如果回复的评论id不为空，判断一下是否存在这个评论
    if (!StringUtils.isEmpty(commentId)) {
      Comment _comment = commentService.findById(commentId);
      ApiAssert.notNull(_comment, "回复的评论不存在");
      if (!topic.getUserId().equals(_comment.getUserId())) {
        // 创建通知 给话题作者发通知
        notificationService.create(topicId, user.getId(), targetUserId, action);
      }
      targetUserId = _comment.getUserId();
      action = "REPLY";
      // 创建通知 给回复对象的作者发通知
      notificationService.create(topicId, user.getId(), targetUserId, action);
    } else {
      // 创建通知 给话题作者发通知
      notificationService.create(topicId, user.getId(), targetUserId, action);
    }
    Comment comment = new Comment();
    comment.setCommentId(commentId);
    comment.setContent(Jsoup.clean(content, Whitelist.none().addTags("br")));
    comment.setInTime(new Date());
    comment.setTopicId(topicId);
    comment.setUserId(user.getId());
    commentService.save(comment);
    // 更新话题的评论数
    topic.setCommentCount(topic.getCommentCount() + 1);
    topicService.save(topic);
    //更新用户的积分
    user.setScore(user.getScore() + siteConfig.getCreateCommentScore());
    userService.save(user);
    // 把保存成功的评论返回给前端
    comment.setUser(user);
    return Result.success(comment);
  }

  @PostMapping("/update")
  public Result update(String id, String content) {
    User user = getUser();
    ApiAssert.notEmpty(id, "要修改的评论ID不能为空");
    ApiAssert.notEmpty(content, "要修改的评论内容不能为空");
    Comment comment = commentService.findById(id);
    ApiAssert.notNull(comment, "评论不存在");
    ApiAssert.isTrue(user.getId().equals(comment.getUserId())
        || siteConfig.getAdmin().contains(user.getUsername()), "不能修改别人的评论");
    comment.setContent(Jsoup.clean(content, Whitelist.none()));
    commentService.save(comment);
    return Result.success();
  }

  @GetMapping("/delete")
  public Result delete(String id) {
    User user = getUser();
    Comment comment = commentService.findById(id);
    ApiAssert.isTrue(user.getId().equals(comment.getUserId())
        || siteConfig.getAdmin().contains(user.getUsername()), "不能删除别人的评论");
    User commentUser = userService.findById(comment.getUserId());
    // 更新话题的评论数
    Topic topic = topicService.findById(comment.getTopicId());
    topic.setCommentCount(topic.getCommentCount() - 1);
    topicService.save(topic);
    // 删除评论
    commentService.deleteById(id);
    //更新用户的积分
    commentUser.setScore(commentUser.getScore() - siteConfig.getCreateCommentScore());
    userService.save(commentUser);
    return Result.success();
  }
}
