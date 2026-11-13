package com.alicia.cloudstorage.rag.assistant;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Separates language about an operation from a request to execute that operation.
 * These guards run before model/local intent arbitration so a high-confidence verb
 * match cannot turn negation, explanation, ambiguity, or cancellation into a plan.
 */
final class NonExecutableRequestGuard {

    private final CompoundMutationScopeGuard compoundMutationScopeGuard = new CompoundMutationScopeGuard();
    private final UnsupportedSubsetScopeGuard unsupportedSubsetScopeGuard = new UnsupportedSubsetScopeGuard();

    private static final Pattern MUTATION = Pattern.compile(
            "删除|删掉|删了|删|移除|清理|清空|回收站|移动|移到|搬到|挪到|归档到|重命名|改名|分享|上传|新建|创建|"
                    + "delete|remove|move|rename|share|upload|create",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern NEGATED_MUTATION = Pattern.compile(
            "(?:^|[，,。；;]|但|但是|不过|可是)(?:我\\s*)?(?:(?:暂时|目前|现在|当前|这次)\\s*)?(?:先)?"
                    + "(?:不要|别|不用|无需|不必|并不是要|不是要|并非要)(?:再)?[^，,。；;]{0,16}"
                    + "(?:删除|删(?:掉|了)?|移除|清理|移动|移到|移去|移走|搬到|搬去|挪到|挪去|归档到|重命名|改名|分享|上传|新建|创建)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern NEGATION_CUE = Pattern.compile(
            "不要|别|不用|无需|不必|不再|不能|不许|不准",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern AMBIGUOUS_DOUBLE_NEGATION_MUTATION = Pattern.compile(
            "(?:不是|并非|也不是).{0,4}(?:不让|不要|不能|不准|不许|不需要).{0,12}"
                    + "(?:删除|删掉|移除|清理|移动|移到|搬到|挪到|归档到|重命名|改名|分享|上传|新建|创建)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern INLINE_FINAL_CANCELLATION = Pattern.compile(
            "(?=.*(?:删除|删掉|移除|清理|移动|移到|搬到|挪到|归档到|重命名|改名|分享|上传|新建|创建))"
                    + "(?=.*(?:(?:算了|改主意了).{0,12}(?:别|不要|不用|什么都不要)|"
                    + "(?:但|但是|不过|可是).{0,8}(?:先)?(?:别|不要|不用).{0,12}"
                    + "(?:删除|删|移动|移|搬|挪|归档|重命名|改名|分享|上传|新建|创建))).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern HYPOTHETICAL_LEAD = Pattern.compile(
            "^(?:如果|假如|假设|要是)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern QUESTION_END = Pattern.compile(
            "(?:会怎样|会发生什么|会不会|什么后果|什么影响|怎么样|如何|吗|呢|[？?])(?:[。！!]*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern QUOTED_OPERATION = Pattern.compile(
            "[“\"'‘].*(?:删除|删掉|移动|重命名|改名|分享|上传|新建|创建|delete|remove|move|rename|share|upload|create).*"
                    + "[”\"'’].*(?:这句话|什么意思|是什么意思|含义|怎么理解|改写|重写|润色|换个说法)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern QUOTED_OPERATION_EDIT = Pattern.compile(
            "(?:改写|重写|润色|换个说法).{0,24}[“\\\"'‘].*(?:删除|删掉|清理|清空|移除|移动|重命名|改名|分享|上传|新建|创建|delete|remove|move|rename|share|upload|create).*[”\\\"'’]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern QUOTED_OPERATION_RISK_REVIEW = Pattern.compile(
            "[“\"'‘].*(?:删除|删掉|清理|清空|移除|移动|重命名|改名|分享|上传|新建|创建|delete|remove|move|rename|share|upload|create).*"
                    + "[”\"'’].*(?:风险|是否安全|安全性|危险|能不能执行|可否执行)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern QUOTED_OPERATION_WRITING_REQUEST = Pattern.compile(
            "(?=.*[“\"'‘].*(?:删除|删掉|清理|清空|移除|移动|重命名|改名|分享|上传|新建|创建|delete|remove|move|rename|share|upload|create).*[”\"'’])"
                    + "(?=.*(?:(?:改成|写成).{0,12}(?:提示|文案|提醒|通知)|只改(?:文案|文字|措辞))).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern OPERATION_PROCESS_QUESTION = Pattern.compile(
            "(?=.*(?:了解|想知道|请问|询问|说明|介绍|说说|告诉我))"
                    + "(?=.*(?:删除|删掉|清理|移除|移动|重命名|改名|分享|上传|新建|创建))"
                    + "(?=.*(?:确认.{0,6}(?:哪些|什么|内容|事项|流程|步骤|规则|机制|方式)|"
                    + "(?:流程|步骤|规则|机制|方式|内容|事项).{0,6}确认)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern EXECUTION_POLICY_EXPLANATION = Pattern.compile(
            "(?:"
                    + "(?=.*(?:为什么|为何|原因|解释|说明|是为了))"
                    + "(?=.*(?:直接.{0,10}(?:修改|改动|执行|操作)|"
                    + "(?:意图|识别).{0,18}(?:确认|执行|分开)|"
                    + "(?:文件操作|创建文件夹|删除文件|任务).{0,18}(?:确认|执行|待确认|关闭)|"
                    + "(?:删除|清理|移动|归档|重命名|改名|分享|上传|新建|创建)(?:文件|文件夹)?操作?.{0,18}(?:确认|执行|待确认|关闭)|"
                    + "(?:确认|待确认).{0,18}(?:执行|任务|状态)|"
                    + "(?:不确认|未确认|没有确认|超时).{0,18}(?:执行|关闭|任务)))"
                    + "|(?=.*(?:不确认|未确认|没有确认|超时))"
                    + "(?=.*(?:任务|执行|关闭))"
                    + "(?=.*(?:会不会|是否|为什么|为何|怎样|怎么|如何|吗|呢|[？?]))"
                    + "|(?=.*(?:why|explain))(?=.*(?:file changes?|file operations?|confirmation|confirm|execute|immediately))"
                    + ").*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern EXECUTION_TIMING_QUESTION = Pattern.compile(
            "(?=.*(?:删除|删掉|移动|归档|重命名|改名|分享|上传|新建|创建))"
                    + "(?=.*(?:什么时候|何时|哪个阶段|何种情况下).{0,12}(?:真正|实际)?执行)"
                    + "(?=.*(?:系统|任务|操作|会|才|[？?])).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern OPERATION_REQUIREMENTS_QUESTION = Pattern.compile(
            "(?=.*(?:删除|删掉|移动|归档|重命名|改名|分享|上传|新建|创建))"
                    + "(?=.*(?:需要|要).{0,10}(?:提供|补充|填写|说明).{0,8}(?:什么|哪些)|"
                    + "(?:需要|要)(?:提供|补充|填写|说明)?(?:什么|哪些))"
                    + "(?=.*(?:告诉我|说明|介绍|只|先别|不要|不实际|[？?])).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern EXPLICIT_CAPABILITY_ONLY_QUESTION = Pattern.compile(
            "(?=.*(?:can\\s+you|能力|支持|你能|能否|能不能|是否能|可不可以))"
                    + "(?=.*(?:files?|文件|delete|remove|move|rename|share|upload|create|删除|移动|重命名|改名|分享|上传|创建))"
                    + "(?=.*(?:(?:只是|只|仅仅|only).{0,10}(?:问|咨询|了解|asking|ask)|"
                    + "(?:不要|不用|无需|不必|不).{0,8}(?:操作|执行)|"
                    + "(?:先)?别(?:再|真的)?(?:操作|执行)|don['’]?t.{0,8}(?:execute|change))).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern DEFERRED_OPERATION = Pattern.compile(
            "(?=.*(?:等|待).{0,16}(?:确认|同意|决定|通知|审批|批准|通过).{0,8}(?:再|后))"
                    + "(?=.*(?:删(?:除|掉|了)?|移动|归档|重命名|改名|分享|上传|新建|创建))"
                    + "(?=.*(?:(?:现在|目前|当前|这次).{0,8}(?:不要动|别动|先不|不操作|不执行)|"
                    + "(?:先)?(?:不要动|别动))).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern EXPLICIT_STOP = Pattern.compile(
            "(?=.*(?:^|[，,。；;])(?:可以|好|好的|行|明白|收到)(?:[，,。；;]|$))"
                    + "(?=.*(?:(?:不要|别|不用).{0,8}(?:执行|操作)|停在这里|到这里为止)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern OPERATION_CAPABILITY_QUESTION = Pattern.compile(
            "(?=.*(?:删除|删掉|清理|移除|移动|归档|重命名|改名|分享|上传|新建|创建))"
                    + "(?=.*(?:之前|以前|执行前|操作前|过程中|流程|步骤|规则|机制|"
                    + "(?:删除|清理|移动|归档|重命名|改名|分享|上传|新建|创建)前))"
                    + "(?=.*(?:(?:会|将会).{0,16}(?:吗|怎样|怎么|如何|是否)|是否|什么|哪些|怎么|怎样|如何|吗|呢|[？?])).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern OPERATION_CONFIRMATION_QUESTION = Pattern.compile(
            "(?=.*(?:删除|删掉|清理|移除|移动|挪动|归档|整理|重命名|改名|分享|上传|新建|创建))"
                    + "(?=.*(?:(?:会|是否|要不要).{0,16}(?:确认|核对|征求)|(?:确认|核对).{0,10}(?:哪些|什么)))"
                    + "(?=.*(?:吗|哪些|什么|是否|怎样|怎么|如何|[？?])).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern GENERAL_OPERATION_CAPABILITY_QUESTION = Pattern.compile(
            "(?=.*(?:(?:文件|组合|批量|逐个).{0,6}操作|一次组合操作))"
                    + "(?=.*(?:确认|步骤|目标|补充|展示|流程|区别))"
                    + "(?=.*(?:什么|哪些|怎样|怎么|如何|区别|情况|会|[？?])).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern MULTI_TURN_RECOGNITION_CAPABILITY_QUESTION = Pattern.compile(
            "(?=.*(?:你能|能否|是否能|支不支持|支持))"
                    + "(?=.*(?:识别|理解|处理))"
                    + "(?=.*(?:连续|多轮|上下文))"
                    + "(?=.*(?:文件操作|操作))"
                    + "(?=.*(?:吗|么|如何|怎样|怎么|[？?])).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern CONSULTATION_ONLY_CAPABILITY_QUESTION = Pattern.compile(
            "(?=.*(?:只想|只是|仅想|仅仅想).{0,8}(?:咨询|了解|问问))"
                    + "(?=.*(?:不执行|不操作|不改动|不修改))"
                    + "(?=.*(?:你能|你可以|能否|可以).{0,12}(?:怎么|怎样|如何|哪些|什么).{0,12}(?:帮忙|帮助|协助|提供)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern HYPOTHETICAL_QUESTION_CUE = Pattern.compile(
            "(?:通常会|会|将会|应该|需要).{0,20}(?:怎样|怎么|如何|是否|确认)|"
                    + "(?:怎样|怎么|如何|是否).{0,20}(?:确认|处理|执行)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern ASSISTANT_IDENTITY_SCOPE_QUESTION = Pattern.compile(
            "(?=.*(?:安安|你|云盘助手|文件助手))"
                    + "(?=.*(?:(?:职责|责任)(?:范围)?|负责什么|什么职责|什么角色|扮演.{0,4}角色|身份|定位|"
                    + "什么类型的助手|(?:能|可以|会).{0,8}(?:帮|帮助|协助).{0,8}(?:什么|干嘛|哪些)))"
                    + "(?!.*(?:详细|具体|举例|例子|哪些.{0,8}(?:操作|方式|方案))).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern OUT_OF_SCOPE_FORMAL_PROOF_REQUEST = Pattern.compile(
            "(?=.*(?:证明|求证|完整推导|彻底解决))"
                    + "(?=.*(?:猜想|未解(?:问题|难题)|千禧年(?:问题|难题))).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern OUT_OF_SCOPE_PHYSICAL_TECH_SUPPORT = Pattern.compile(
            "(?=.*(?:修(?:一下|理)?|维修|排查|诊断|检修))"
                    + "(?=.*(?:路由器|打印机|电脑硬件|显示器|电视|空调|冰箱|洗衣机|汽车|家电)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern OUT_OF_SCOPE_HIGH_STAKES_ADVICE = Pattern.compile(
            "(?:(?=.*(?:预测|推荐|判断|分析))(?=.*(?:股票|基金|投资|涨跌|行情))|"
                    + "(?=.*(?:诊断|判断|分析|治疗建议))(?=.*(?:病因|原因|疼|痛|症状|疾病|身体|膝盖|头痛|胃痛))).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern PRODUCT_FILE_FEATURE_QUESTION = Pattern.compile(
            "(?!.*(?:哪些.{0,8}(?:文件类型|类型|格式)|文件类型|格式|整个文件夹|多大|大小|限制|条件))"
                    + "(?=.*(?:Alicia|网盘|云盘|这个产品))"
                    + "(?=.*(?:(?:支持|能否|是否能|能不能|可以|有没有)|"
                    + "(?:能.{0,6}(?:保存|保留|查看|恢复).{0,4}(?:版本历史|历史版本))))"
                    + "(?=.*(?:搜索|查找|分享|上传|下载|移动|重命名|改名|分类|整理|版本历史|历史版本))"
                    + "(?=.*(?:吗|么|哪些|什么|如何|怎么|[？?])).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern ASSISTANT_ORGANIZATION_CAPABILITY_QUESTION = Pattern.compile(
            "(?=.*(?:你|安安))(?=.*(?:能|可以|支持))"
                    + "(?=.*按.{0,8}(?:类型|条件).{0,8}(?:整理|分类))"
                    + "(?=.*(?:吗|么|如何|怎么|怎样|[？?])).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern EXTERNAL_CLOUD_RESOURCE_REQUEST = Pattern.compile(
            "(?=.*(?:外部|别的|其他|第三方).{0,8}(?:网盘|云盘))"
                    + "(?=.*(?:资源|链接|地址))"
                    + "(?=.*(?:给我|找|查找|推荐|提供|发一个|来一个)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern PRODUCT_OVERVIEW_QUESTION = Pattern.compile(
            "(?=.*(?:Alicia|这个云盘|该云盘|这个产品))"
                    + "(?=.*(?:主要|核心|用来|解决|作用))"
                    + "(?=.*(?:什么问题|做什么|用途|功能|价值)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern PUBLIC_EXTERNAL_RESOURCE_REQUEST = Pattern.compile(
            "(?=.*(?:哪里|哪儿|什么地方|渠道))(?=.*(?:下载|找到|获取))"
                    + "(?=.*(?:公开|行业报告|外部报告|资料)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern WRITING_HELP_REQUEST = Pattern.compile(
            "(?=.*(?:帮我|请|能否|可以))(?=.*(?:写一句|改得|改写|润色|换个说法))"
                    + "(?=.*(?:礼貌|委婉|简短|正式|提醒|文案|这句话|[“\"'])).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern PERSONAL_CHAT_REQUEST = Pattern.compile(
            "(?:(?=.*(?:事情|工作|状态|心情))(?=.*(?:有点多|缓一缓|累|乱))|"
                    + "(?=.*(?:先停一下|暂停一下))(?=.*(?:累|休息|缓缓))).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern CLOUD_FILE_CONTEXT = Pattern.compile(
            "(?:搜索|查找|检索|文件名|文件夹|目录|云盘|上传|删除|移动|重命名|改名|分享|打开文件|下载文件)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern AMBIGUOUS_OPERATION = Pattern.compile(
            "(?:处理|处置)(?:一下|掉)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern VAGUE_ORGANIZATION = Pattern.compile(
            "^(?:(?=.*(?:这些|那些|这批|那批))(?=.*(?:资料|文件|东西))"
                    + "(?=.*(?:整理一下|收拾一下))(?=.*(?:看着|随便|一下)).*|"
                    + "(?:把)?(?:它们|这些|那些|这批|那批)(?:给我)?(?:妥善)?放好(?:就行|即可|吧)?)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern PAUSED_FILE_CONVERSATION = Pattern.compile(
            "(?:(?=.*(?:先不|暂时不|现在不).{0,8}(?:处理|操作).{0,8}(?:文件|资料))|"
                    + "(?=.*(?:文件|资料).{0,8}(?:先)?(?:放一边|放着|搁一边|先不管|晚点处理)))"
                    + "(?=.*(?:聊|说说话|说点别的|聊点别的|状态怎么样|今天怎么样)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern META_OPERATION_DISCLAIMER = Pattern.compile(
            "(?:只是|仅仅是|仅是|只是个|只是一个).{0,12}(?:举例|例子|示例)|"
                    + "(?:不要|别|无需|不必).{0,12}(?:真的)?(?:执行|操作)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern STRUCTURED_TEXT_EXPLANATION = Pattern.compile(
            "^(?:请)?(?:解释|说明|分析|解读|帮我看(?:看)?).{0,20}(?:JSON|代码|文本|配置|指令).*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern CONTRADICTORY_PRESERVATION = Pattern.compile(
            "(?:删除|删掉|移除|清理).{0,24}(?:保留|不要动|别动|不改|不能动)|"
                    + "(?:保留|不要动|别动|不改|不能动).{0,24}(?:删除|删掉|移除|清理)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern SAFETY_BYPASS = Pattern.compile(
            "(?:忽略|绕过|跳过).{0,16}(?:安全|规则|确认|权限)|"
                    + "(?:不要|无需|不必|跳过).{0,8}确认|"
                    + "(?:直接执行|直接操作).{0,16}(?:告诉我|声称).{0,8}(?:完成|成功)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern QUOTED_NAME_SEARCH = Pattern.compile(
            "(?:找|查找|搜索|检索).*?(?:名字|名称|文件名).*?(?:包含|含有|含|带有|带).*?"
                    + "([“\"'‘][^”\"'’]+[”\"'’])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern ALTERNATIVE_CONNECTOR = Pattern.compile(
            "或者|或是|还是|二选一",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern UNRESOLVED_REFERENCE = Pattern.compile(
            "^(?:请|麻烦)?(?:帮我)?(?:把)?(?:(?:最近|刚才|之前|上次)(?:的)?)?"
                    + "(?:它|这个|那个)(?:文件|文件夹|目录)?(?:给我)?"
                    + "(?:删除|删掉|删了|移除|移动|移到|搬到|挪到|重命名|改名|分享|放到|放进|放进去)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern MUTATION_REFERENCE = Pattern.compile(
            "(?:它|这个|那个)(?:文件夹|目录|文件)?(?=(?:重命名|改名|移动|搬|挪|到|至|进|$))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern IN_UTTERANCE_REFERENCE_ANTECEDENT = Pattern.compile(
            "(?:(?:[\\p{L}\\p{N}_-]+\\.[A-Za-z0-9]{1,12})|"
                    + "(?:名为|名称为|叫做|叫)[“\\\"'‘]?[^，,；;”\\\"'’]{1,64}[”\\\"'’]?)"
                    + "[^，,；;]{0,24}[，,；;].{0,24}(?:它|这个|那个)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern RENAME_WITH_NEW_NAME = Pattern.compile(
            "(?:重命名为|重命名成|改名为|改名成|改成|改为)\\s*([^，,。；;!?！？]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern MOVE_DESTINATION = Pattern.compile(
            "(?:到|至|进)\\s*([^，,。；;!?！？]+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern CANCELLATION = Pattern.compile(
            "^(?:(?:算了[，,]?)?(?:不用了|不做了|先不做了|取消(?:这个|该|刚才的)?(?:操作|任务|动作|删除|移动|上传|创建|新建|重命名|改名)?|停止|停下|撤销|作罢)(?:[，,].*)?|"
                    + "(?:算了[，,]?)?(?:先)?取消(?:删除|移动|上传|创建|新建|重命名|改名)(?:[，,].*)?|"
                    + "(?:不建了|别建了)(?:[，,].*)?|"
                    + "(?:停一下|停止)[，,]?(?:这批|这些|那些)?(?:文件)?(?:先)?(?:不要|别)(?:再)?(?:删除|移动|上传|创建|新建)|"
                    + "(?:算了[，,]?)?(?:(?:这个|该|刚才的)(?:文件夹|目录|操作|任务)?\\s*)?"
                    + "(?:先)?(?:不要|别|不用)(?:再)?(?:建|创建|新建|继续|执行|移动|上传|删除)(?:了)?|"
                    + "(?:不用了[，,]?)?(?:先)?(?:不建|别建)(?:了)?|算了)[。！!]*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern CANCEL_ALL_PENDING_STEPS = Pattern.compile(
            "^(?=.*(?:两个|全部|所有).{0,8}(?:步骤|操作|动作))(?=.*(?:都)?取消)"
                    + "(?=.*(?:(?:什么|任何).{0,4}(?:也)?(?:别|不要|不用)(?:做|执行|操作)|"
                    + "(?:别|不要|不用).{0,6}(?:做|执行|操作))).*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern COMPOSITE_CONFIRMATION_EXPLANATION = Pattern.compile(
            "(?=.*(?:组合|多个步骤|两步|先.+再.+))(?=.*(?:确认|审批))"
                    + "(?=.*(?:几次|多少次|一次还是|是否分别|要不要分别|[？?])).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern CONTEXTLESS_CONFIRMATION = Pattern.compile(
            "^(?:(?:好|好的|行|可以)[，,]?)?(?:请)?"
                    + "(?:确认(?:并|后)?执行|确认执行|确认|执行|(?:那)?就(?:这样|这么)执行|"
                    + "(?:(?:还是|那?就)?(?:按|照)(?:(?:刚才|之前|前面)(?:的)?)?"
                    + "(?:说的|这句话|那句话|这个|那个)?(?:直接)?(?:执行|继续|做))|"
                    + "继续(?:刚才|之前)(?:那个|这个|说的)?|"
                    + "(?:那)?继续|"
                    + "(?:继续|接着)(?:处理|操作|执行)(?:一下)?(?:它|这个|那个)?)"
                    + "(?:吧|了)?[。！!]*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final List<Pattern> DISTINCT_OPERATIONS = List.of(
            Pattern.compile("删除|删掉|删了|移除|清理|回收站"),
            Pattern.compile("移动|移到|搬到|挪到|归档到|放到|放进"),
            Pattern.compile("重命名|改名"),
            Pattern.compile("分享"),
            Pattern.compile("上传"),
            Pattern.compile("新建|创建")
    );

    Optional<Decision> evaluate(String message, AssistantConversationState conversation) {
        String value = message == null ? "" : message.trim();
        if (value.isBlank()) {
            return Optional.empty();
        }
        if (CANCEL_ALL_PENDING_STEPS.matcher(value).matches() && hasPendingOperation(conversation)) {
            return Optional.of(new Decision(
                    "cancel_all_pending_steps",
                    "assistant_acknowledgement",
                    "CANCELLATION",
                    "RESPOND",
                    "好的，这组操作已经全部取消，我不会提交任何文件变更。"
            ));
        }
        if (CANCELLATION.matcher(value).matches() && hasPendingOperation(conversation)) {
            return Optional.of(new Decision(
                    "cancel_pending_operation",
                    "assistant_acknowledgement",
                    "CANCELLATION",
                    "RESPOND",
                    "好的，这次操作已取消，我不会继续提交文件变更。"
            ));
        }
        if (CONTEXTLESS_CONFIRMATION.matcher(value).matches() && !hasPendingOperation(conversation)) {
            return Optional.of(new Decision(
                    "confirmation_without_pending_operation",
                    "fallback",
                    "CONFIRMATION",
                    "UNKNOWN",
                    "当前没有等待确认的文件操作。请先说明要执行的操作和目标。"
            ));
        }
        if (EXPLICIT_STOP.matcher(value).matches()) {
            return Optional.of(new Decision(
                    "explicit_non_execution_stop",
                    "assistant_acknowledgement",
                    "CANCELLATION",
                    "RESPOND",
                    "好的，我会停在这里，不会提交或执行任何文件操作。"
            ));
        }
        if (DEFERRED_OPERATION.matcher(value).matches()) {
            return Optional.of(new Decision(
                    "deferred_operation",
                    "assistant_acknowledgement",
                    "CANCELLATION",
                    "RESPOND",
                    "好的，现在不会执行；等你之后明确提出并确认时再重新处理。"
            ));
        }
        if (PERSONAL_CHAT_REQUEST.matcher(value).matches()) {
            return Optional.of(new Decision(
                    "personal_chat_request",
                    "assistant_chat",
                    "NEW_TASK",
                    "RESPOND",
                    "可以，我们先暂停文件处理。你可以说说现在的感受或安排。"
            ));
        }
        if (WRITING_HELP_REQUEST.matcher(value).matches() && !MUTATION.matcher(value).find()) {
            return Optional.of(new Decision(
                    "writing_help_request",
                    "assistant_writing_help",
                    "NEW_TASK",
                    "RESPOND",
                    "可以，我只会帮助调整文字表达，不会执行文件操作。"
            ));
        }
        if (ASSISTANT_IDENTITY_SCOPE_QUESTION.matcher(value).matches()) {
            return Optional.of(new Decision(
                    "assistant_identity_scope_question",
                    "assistant_identity",
                    "NEW_TASK",
                    "RESPOND",
                    "我是 Alicia 云盘中的智能文件管家安安，可以说明我的职责、能力边界和安全确认方式。"
            ));
        }
        if (PRODUCT_OVERVIEW_QUESTION.matcher(value).matches()) {
            return Optional.of(new Decision(
                    "product_overview_question",
                    "assistant_product_info",
                    "NEW_TASK",
                    "RESPOND",
                    "Alicia 云盘用于统一保存、查找和整理文件，并通过确认流程降低误操作风险。"
            ));
        }
        if (PUBLIC_EXTERNAL_RESOURCE_REQUEST.matcher(value).matches()) {
            return Optional.of(new Decision(
                    "public_external_resource_request",
                    "assistant_external_resource",
                    "NEW_TASK",
                    "RESPOND",
                    "我不能代为检索或下载外部网站资源，但可以建议你使用公开、正规且有授权的来源。"
            ));
        }
        if (OUT_OF_SCOPE_FORMAL_PROOF_REQUEST.matcher(value).matches()
                && !CLOUD_FILE_CONTEXT.matcher(value).find()) {
            return Optional.of(new Decision(
                    "out_of_scope_formal_proof_request",
                    "fallback",
                    "NEW_TASK",
                    "UNKNOWN",
                    "这类开放数学问题不属于云盘文件管理能力，我不能生成或声称完成尚无公认证明的结论。"
            ));
        }
        if (OUT_OF_SCOPE_PHYSICAL_TECH_SUPPORT.matcher(value).matches()
                && !CLOUD_FILE_CONTEXT.matcher(value).find()) {
            return Optional.of(new Decision(
                    "out_of_scope_physical_tech_support",
                    "fallback",
                    "NEW_TASK",
                    "UNKNOWN",
                    "这类实体设备维修不属于云盘文件管理能力。请联系对应设备的技术支持或维修服务。"
            ));
        }
        if (OUT_OF_SCOPE_HIGH_STAKES_ADVICE.matcher(value).matches()
                && !CLOUD_FILE_CONTEXT.matcher(value).find()) {
            return Optional.of(new Decision(
                    "out_of_scope_high_stakes_advice",
                    "fallback",
                    "NEW_TASK",
                    "UNKNOWN",
                    "这类金融预测或医疗诊断不属于云盘文件管理能力，我不会给出可能误导你的专业结论。"
            ));
        }
        if (EXTERNAL_CLOUD_RESOURCE_REQUEST.matcher(value).matches()) {
            return Optional.of(new Decision(
                    "external_cloud_resource_request",
                    "assistant_external_resource",
                    "NEW_TASK",
                    "RESPOND",
                    "我不能代为查找或提供第三方网盘资源链接，但可以帮助你搜索自己云盘中已有的文件。"
            ));
        }
        if (PRODUCT_FILE_FEATURE_QUESTION.matcher(value).matches()) {
            return Optional.of(new Decision(
                    "product_file_feature_question",
                    "assistant_product_info",
                    "NEW_TASK",
                    "RESPOND",
                    "这是在询问 Alicia 的产品功能，我只会说明支持范围，不会把它当成文件操作。"
            ));
        }
        if (ASSISTANT_ORGANIZATION_CAPABILITY_QUESTION.matcher(value).matches()) {
            return Optional.of(new Decision(
                    "assistant_organization_capability_question",
                    "assistant_capability_examples",
                    "NEW_TASK",
                    "RESPOND",
                    "我可以说明按类型或条件整理文件的能力边界；这次只做说明，不会提交文件变更。"
            ));
        }
        String guardedValue = value;
        var quotedName = QUOTED_NAME_SEARCH.matcher(value);
        if (quotedName.find()) {
            guardedValue = value.substring(0, quotedName.start(1))
                    + "“文件名”"
                    + value.substring(quotedName.end(1));
        }
        if (QUOTED_OPERATION_WRITING_REQUEST.matcher(guardedValue).matches()) {
            return Optional.of(new Decision(
                    "quoted_operation_writing_request",
                    "assistant_writing_help",
                    "NEW_TASK",
                    "RESPOND",
                    "我只会把引号中的内容作为文案改写，不会执行其中描述的文件操作。"
            ));
        }
        if (QUOTED_OPERATION_RISK_REVIEW.matcher(guardedValue).find()) {
            return Optional.of(new Decision(
                    "quoted_operation_risk_review",
                    "assistant_capability_examples",
                    "NEW_TASK",
                    "RESPOND",
                    "这是一条需要安全审查的文件操作描述，我只会说明风险和确认要求，不会执行其中的操作。"
            ));
        }
        if (QUOTED_OPERATION.matcher(guardedValue).find()
                || QUOTED_OPERATION_EDIT.matcher(guardedValue).find()) {
            return Optional.of(new Decision(
                    "quoted_operation_explanation",
                    "assistant_writing_help",
                    "NEW_TASK",
                    "RESPOND",
                    "这句话是在描述一个文件操作；这里只解释它的含义，不会执行其中的操作。"
            ));
        }
        if (MUTATION.matcher(guardedValue).find() && STRUCTURED_TEXT_EXPLANATION.matcher(guardedValue).matches()) {
            return Optional.of(new Decision(
                    "structured_operation_explanation",
                    "assistant_writing_help",
                    "NEW_TASK",
                    "RESPOND",
                    "这段内容只会作为文本进行解释，不会把其中的字段或命令当成文件操作。"
            ));
        }
        if (EXECUTION_POLICY_EXPLANATION.matcher(guardedValue).matches()) {
            return Optional.of(new Decision(
                    "execution_policy_explanation",
                    "assistant_execution_explanation",
                    "NEW_TASK",
                    "RESPOND",
                    "文件变更会把意图识别和实际执行分开：我先整理目标、范围和步骤，只有你明确确认后，云端任务才会执行。未确认或超时的任务不会改动文件，这样既能避免误操作，也便于取消和审计。"
            ));
        }
        if (COMPOSITE_CONFIRMATION_EXPLANATION.matcher(guardedValue).matches()) {
            return Optional.of(new Decision(
                    "composite_confirmation_explanation",
                    "assistant_execution_explanation",
                    "NEW_TASK",
                    "RESPOND",
                    "组合计划会先完整展示步骤和影响范围，再按一个整体计划等待确认；当前只说明确认规则，不会创建执行计划。"
            ));
        }
        if (EXECUTION_TIMING_QUESTION.matcher(guardedValue).matches()) {
            return Optional.of(new Decision(
                    "execution_timing_question",
                    "assistant_execution_explanation",
                    "NEW_TASK",
                    "RESPOND",
                    "文件操作只有在计划完整、权限校验通过并由你明确确认后才会真正执行。"
            ));
        }
        if (OPERATION_REQUIREMENTS_QUESTION.matcher(guardedValue).matches()
                || EXPLICIT_CAPABILITY_ONLY_QUESTION.matcher(guardedValue).matches()) {
            return Optional.of(new Decision(
                    "operation_requirements_question",
                    "assistant_capability_examples",
                    "NEW_TASK",
                    "RESPOND",
                    "我可以说明操作所需的信息和能力边界；这次只做说明，不会提交文件变更。"
            ));
        }
        if (OPERATION_PROCESS_QUESTION.matcher(guardedValue).matches()
                && !SAFETY_BYPASS.matcher(guardedValue).find()) {
            return Optional.of(new Decision(
                    "operation_process_question",
                    "assistant_capability_examples",
                    "NEW_TASK",
                    "RESPOND",
                    "我可以说明文件操作的确认流程和安全规则；这次只做说明，不会提交任何变更。"
            ));
        }
        if (MUTATION.matcher(guardedValue).find() && META_OPERATION_DISCLAIMER.matcher(guardedValue).find()) {
            return Optional.of(new Decision(
                    "operation_disclaimer",
                    "assistant_acknowledgement",
                    "NEW_TASK",
                    "RESPOND",
                    "明白，这只是示例或说明，我不会执行其中的文件操作。"
            ));
        }
        if (MUTATION.matcher(guardedValue).find() && SAFETY_BYPASS.matcher(guardedValue).find()) {
            return Optional.of(new Decision(
                    "safety_bypass_request",
                    "fallback",
                    "NEW_TASK",
                    "UNKNOWN",
                    "我不会绕过确认、安全规则或权限执行文件操作。请按正常流程明确目标并在预览后确认。"
            ));
        }
        if (OPERATION_CAPABILITY_QUESTION.matcher(guardedValue).matches()) {
            return Optional.of(new Decision(
                    "operation_capability_question",
                    "assistant_capability_examples",
                    "NEW_TASK",
                    "RESPOND",
                    "我可以说明文件操作前如何确定目标、生成预览并等待确认；这次只解释流程，不会提交变更。"
            ));
        }
        if (OPERATION_CONFIRMATION_QUESTION.matcher(guardedValue).matches()
                && !SAFETY_BYPASS.matcher(guardedValue).find()
                && !HYPOTHETICAL_LEAD.matcher(value).find()) {
            return Optional.of(new Decision(
                    "operation_confirmation_question",
                    "assistant_capability_examples",
                    "NEW_TASK",
                    "RESPOND",
                    "我可以说明操作前会核对哪些信息以及如何确认；这次只解释流程，不会提交变更。"
            ));
        }
        if (GENERAL_OPERATION_CAPABILITY_QUESTION.matcher(guardedValue).matches()) {
            return Optional.of(new Decision(
                    "general_operation_capability_question",
                    "assistant_capability_examples",
                    "NEW_TASK",
                    "RESPOND",
                    "我可以说明文件操作如何补充目标、展示步骤并等待确认；这次只解释流程，不会提交变更。"
            ));
        }
        if (MULTI_TURN_RECOGNITION_CAPABILITY_QUESTION.matcher(guardedValue).matches()) {
            return Optional.of(new Decision(
                    "multi_turn_recognition_capability_question",
                    "assistant_capability_examples",
                    "NEW_TASK",
                    "RESPOND",
                    "我可以说明连续多轮对话中怎样保留文件操作上下文；这次只做能力说明，不会提交变更。"
            ));
        }
        if (CONSULTATION_ONLY_CAPABILITY_QUESTION.matcher(guardedValue).matches()) {
            return Optional.of(new Decision(
                    "consultation_only_capability_question",
                    "assistant_capability_examples",
                    "NEW_TASK",
                    "RESPOND",
                    "我可以提供能力范围、可选操作和安全确认方式的说明；这次只做咨询，不会提交变更。"
            ));
        }
        Optional<UnsupportedSubsetScopeGuard.Decision> subsetDecision =
                unsupportedSubsetScopeGuard.evaluate(guardedValue);
        if (!HYPOTHETICAL_LEAD.matcher(value).find()
                && subsetDecision.map(decision -> "unsupported_exclusion_remainder".equals(decision.id())).orElse(false)) {
            return Optional.of(subsetDecision(subsetDecision.orElseThrow()));
        }
        if (AMBIGUOUS_DOUBLE_NEGATION_MUTATION.matcher(guardedValue).find()) {
            return Optional.of(new Decision(
                    "ambiguous_double_negation_operation",
                    "fallback",
                    "NEW_TASK",
                    "UNKNOWN",
                    "这句话包含双重否定，我无法安全判断你是否真的要修改文件。请直接说明要执行或不要执行的操作。"
            ));
        }
        if (INLINE_FINAL_CANCELLATION.matcher(guardedValue).matches()) {
            return Optional.of(new Decision(
                    "inline_final_cancellation",
                    "assistant_acknowledgement",
                    "CANCELLATION",
                    "RESPOND",
                    "好的，我按最后的意思取消这项文件操作，不会提交任何变更。"
            ));
        }
        if (CONTRADICTORY_PRESERVATION.matcher(guardedValue).find()) {
            return Optional.of(new Decision(
                    "contradictory_preservation",
                    "fallback",
                    "NEW_TASK",
                    "UNKNOWN",
                    "这句话同时要求删除和保留同一对象。请明确选择一种结果，我不会先执行任何操作。"
            ));
        }
        if (AMBIGUOUS_OPERATION.matcher(guardedValue).find()
                && !MUTATION.matcher(guardedValue).find()) {
            return Optional.of(new Decision(
                    "ambiguous_operation",
                    "fallback",
                    "NEW_TASK",
                    "UNKNOWN",
                    "“处理”可能表示删除、移动、重命名或其他操作。请明确要做什么，我不会先执行任何变更。"
            ));
        }
        if (VAGUE_ORGANIZATION.matcher(guardedValue).matches()) {
            return Optional.of(new Decision(
                    "vague_organization",
                    "fallback",
                    "NEW_TASK",
                    "UNKNOWN",
                    "“整理”可能表示移动、分类或重命名。请说明范围和整理方式，我不会先执行任何变更。"
            ));
        }
        if (PAUSED_FILE_CONVERSATION.matcher(guardedValue).matches()) {
            return Optional.of(new Decision(
                    "paused_file_conversation",
                    "assistant_chat",
                    "NEW_TASK",
                    "RESPOND",
                    "好，先不处理文件。我们可以轻松聊一会儿，等你想继续时再告诉我。"
            ));
        }
        if (!value.matches(".*(?:不要忘记|别忘了).*")
                && NEGATED_MUTATION.matcher(guardedValue).find()
                && !hasAffirmativeReplacement(guardedValue)) {
            return Optional.of(new Decision(
                    "negated_operation",
                    "assistant_acknowledgement",
                    "NEW_TASK",
                    "RESPOND",
                    "好的，我不会执行这项文件操作。若要改做其他操作，请重新说明。"
            ));
        }
        if (HYPOTHETICAL_LEAD.matcher(value).find()
                && MUTATION.matcher(value).find()
                && (QUESTION_END.matcher(value).find()
                || HYPOTHETICAL_QUESTION_CUE.matcher(value).find())) {
            return Optional.of(new Decision(
                    "hypothetical_operation",
                    "assistant_capability_examples",
                    "NEW_TASK",
                    "RESPOND",
                    "这是一个假设性问题，我不会执行文件操作。实际变更只会在目标明确并经过你确认后提交。"
            ));
        }
        if (subsetDecision.isPresent()) {
            return Optional.of(subsetDecision(subsetDecision.orElseThrow()));
        }
        if (ALTERNATIVE_CONNECTOR.matcher(value).find() && distinctOperationCount(value) > 1) {
            return Optional.of(new Decision(
                    "conflicting_operations",
                    "fallback",
                    "NEW_TASK",
                    "UNKNOWN",
                    "这句话包含多个互斥操作。请明确选择其中一个，我不会先执行任何操作。"
            ));
        }
        Optional<CompoundMutationScopeGuard.Decision> scopeDecision = compoundMutationScopeGuard.evaluate(value);
        if (scopeDecision.isPresent()) {
            CompoundMutationScopeGuard.Decision decision = scopeDecision.orElseThrow();
            return Optional.of(new Decision(
                    decision.id(),
                    decision.intentId(),
                    decision.relation(),
                    decision.operation(),
                    decision.assistantText(),
                    decision.entities()
            ));
        }
        if (!hasCandidateContext(conversation)
                && hasResolvableMutationReference(value)
                && !IN_UTTERANCE_REFERENCE_ANTECEDENT.matcher(value).find()) {
            return Optional.of(new Decision(
                    "unresolved_rebindable_reference",
                    "fallback",
                    "NEW_TASK",
                    "UNKNOWN",
                    "我会保留这次移动或重命名意图，但需要先确定“这个文件”具体指哪一个。"
            ));
        }
        if (UNRESOLVED_REFERENCE.matcher(value).find() && !hasCandidateContext(conversation)) {
            return Optional.of(new Decision(
                    "unresolved_reference",
                    "fallback",
                    "NEW_TASK",
                    "UNKNOWN",
                    "我还不知道“它”指哪个文件或文件夹。请提供名称，或先从候选中选择目标。"
            ));
        }
        return Optional.empty();
    }

    private Decision subsetDecision(UnsupportedSubsetScopeGuard.Decision decision) {
        return new Decision(
                decision.id(),
                decision.intentId(),
                decision.relation(),
                decision.operation(),
                decision.assistantText(),
                decision.entities()
        );
    }

    private boolean hasAffirmativeReplacement(String message) {
        String[] clauses = message.split("(?:[，,；;]|而是)");
        for (String rawClause : clauses) {
            String clause = rawClause.trim()
                    .replaceFirst("^(?:改为|改成|改做|换成|请|现在|实际|转而|先只|只|仅仅|仅)\\s*", "");
            var mutation = MUTATION.matcher(clause);
            if (clause.isBlank() || !mutation.find()) {
                continue;
            }
            String prefix = clause.substring(0, mutation.start());
            if (!NEGATION_CUE.matcher(prefix).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasResolvableMutationReference(String message) {
        if (!MUTATION_REFERENCE.matcher(message).find()) {
            return false;
        }
        if (RENAME_WITH_NEW_NAME.matcher(message).find()) {
            return true;
        }
        if (!TextSupport.containsAny(message, List.of("移动", "移到", "搬到", "挪到", "搬", "挪"))) {
            return false;
        }
        var destination = MOVE_DESTINATION.matcher(message);
        if (!destination.find()) {
            return false;
        }
        String value = destination.group(1).trim();
        return !List.of("那里", "这里", "那儿", "这儿", "那个", "这个", "某处", "一个地方", "目标目录", "文件夹", "目录")
                .contains(value);
    }

    private int distinctOperationCount(String message) {
        return (int) DISTINCT_OPERATIONS.stream()
                .filter(pattern -> pattern.matcher(message).find())
                .count();
    }

    private boolean hasCandidateContext(AssistantConversationState conversation) {
        return conversation != null
                && conversation.focus() != null
                && conversation.focus().hasCandidateContext();
    }

    private boolean hasPendingOperation(AssistantConversationState conversation) {
        if (conversation == null) {
            return false;
        }
        ActionDraft draft = conversation.pendingActionDraft();
        if (draft != null && draft.type() != null
                && !draft.type().isBlank()
                && !List.of("none", "search").contains(draft.type())) {
            return true;
        }
        return conversation.hasPendingSlots()
                && conversation.pendingIntentId() != null
                && !conversation.pendingIntentId().isBlank();
    }

    record Decision(
            String id,
            String intentId,
            String relation,
            String operation,
            String assistantText,
            Map<String, Object> entities
    ) {
        Decision(String id, String intentId, String relation, String operation, String assistantText) {
            this(id, intentId, relation, operation, assistantText, Map.of());
        }

        Decision {
            entities = entities == null || entities.isEmpty() ? Map.of() : Map.copyOf(entities);
        }

        boolean requiresSemanticResolution() {
            return "unresolved_rebindable_reference".equals(id);
        }
    }
}
