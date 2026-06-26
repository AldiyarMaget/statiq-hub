import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:provider/provider.dart';
import 'package:shimmer/shimmer.dart';
import '../providers/chat_provider.dart';
import '../models/chat_message.dart';
import '../widgets/mermaid_render_widget.dart';

class ChatScreen extends StatefulWidget {
  const ChatScreen({super.key});

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final TextEditingController _messageController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  bool _isSidebarOpen = true;

  @override
  void dispose() {
    _messageController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final provider = Provider.of<ChatProvider>(context);
    final isWideScreen = MediaQuery.of(context).size.width > 800;

    // Trigger scroll when loading state changes or a new message is added
    if (provider.isLoading) {
      _scrollToBottom();
    }

    Widget sidebar = _buildSidebar(context, provider);
    Widget chatArea = _buildChatArea(context, provider, isWideScreen);

    return Scaffold(
      drawer: !isWideScreen ? Drawer(child: sidebar) : null,
      appBar: AppBar(
        titleSpacing: 0,
        elevation: 0,
        backgroundColor: Theme.of(context).scaffoldBackgroundColor,
        leading: !isWideScreen
            ? Builder(
                builder: (context) => IconButton(
                  icon: const Icon(Icons.menu),
                  onPressed: () => Scaffold.of(context).openDrawer(),
                ),
              )
            : IconButton(
                icon: Icon(_isSidebarOpen ? Icons.menu_open : Icons.menu),
                onPressed: () {
                  setState(() {
                    _isSidebarOpen = !_isSidebarOpen;
                  });
                },
              ),
        title: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16.0),
          child: Row(
            children: [
              Icon(
                Icons.auto_awesome,
                color: Theme.of(context).brightness == Brightness.dark
                    ? const Color(0xFFE3E3E3)
                    : Colors.black87,
              ),
              const SizedBox(width: 8),
              const Text(
                'StatIQ',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
              ),
              const SizedBox(width: 12),
              _buildRoleBadge(context, provider),
            ],
          ),
        ),
        actions: [
          Container(
            width: 280,
            padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 12),
            alignment: Alignment.center,
            child: _buildModeSelector(context, provider),
          ),
        ],
      ),
      body: Row(
        children: [
          if (isWideScreen && _isSidebarOpen)
            SizedBox(
              width: 280,
              child: Container(
                decoration: BoxDecoration(
                  border: Border(
                    right: BorderSide(
                      color: Theme.of(context).brightness == Brightness.dark
                          ? Colors.white.withValues(alpha: 0.05)
                          : Theme.of(context).dividerColor,
                      width: 0.5,
                    ),
                  ),
                ),
                child: sidebar,
              ),
            ),
          Expanded(child: chatArea),
        ],
      ),
    );
  }

  // Segmented Mode Selector
  Widget _buildModeSelector(BuildContext context, ChatProvider provider) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return SegmentedButton<String>(
      segments: const [
        ButtonSegment<String>(
          value: 'user',
          label: Text('Пользователь'),
          icon: Icon(Icons.person_outline, size: 16),
        ),
        ButtonSegment<String>(
          value: 'analyst',
          label: Text('Аналитик'),
          icon: Icon(Icons.analytics_outlined, size: 16),
        ),
      ],
      selected: {provider.currentMode},
      onSelectionChanged: (newSelection) {
        provider.setMode(newSelection.first);
      },
      showSelectedIcon: false,
      style: SegmentedButton.styleFrom(
        selectedBackgroundColor: isDark ? const Color(0xFF282A2D) : Colors.black.withValues(alpha: 0.08),
        selectedForegroundColor: isDark ? Colors.white : Colors.black87,
        backgroundColor: Colors.transparent,
        foregroundColor: isDark ? Colors.white54 : Colors.black54,
        side: BorderSide(
          color: isDark ? Colors.white.withValues(alpha: 0.08) : Colors.black12,
          width: 0.8,
        ),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
        visualDensity: VisualDensity.compact,
        textStyle: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold),
      ),
    );
  }

  // Role Badge in AppBar
  Widget _buildRoleBadge(BuildContext context, ChatProvider provider) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final isUser = provider.currentMode == 'user';
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: isDark
            ? const Color(0xFF232428)
            : (isUser ? Colors.blue.withValues(alpha: 0.08) : Colors.indigo.withValues(alpha: 0.08)),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isDark ? Colors.white.withValues(alpha: 0.05) : Colors.transparent,
          width: 0.8,
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 6,
            height: 6,
            decoration: BoxDecoration(
              color: isUser ? const Color(0xFF81D4FA) : const Color(0xFFB39DDB),
              shape: BoxShape.circle,
            ),
          ),
          const SizedBox(width: 6),
          Text(
            isUser ? 'БНС • Пользователь' : 'БНС • Аналитик',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              color: isDark ? const Color(0xFFE3E3E3) : (isUser ? Colors.blue.shade700 : Colors.indigo.shade700),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSidebar(BuildContext context, ChatProvider provider) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      color: isDark ? const Color(0xFF1C1B1F) : const Color(0xFFF8FAFC),
      padding: const EdgeInsets.all(16.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // New Chat Button
          Container(
            decoration: BoxDecoration(
              gradient: isDark
                  ? const LinearGradient(
                      colors: [Color(0xFF282A2D), Color(0xFF232428)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    )
                  : null,
              color: isDark ? null : Colors.white,
              borderRadius: BorderRadius.circular(24),
              border: Border.all(
                color: isDark ? Colors.white.withValues(alpha: 0.08) : Colors.black12,
                width: 0.8,
              ),
            ),
            child: Material(
              color: Colors.transparent,
              child: InkWell(
                borderRadius: BorderRadius.circular(24),
                onTap: () {
                  provider.startNewSession();
                  if (MediaQuery.of(context).size.width <= 800) {
                    Navigator.pop(context);
                  }
                },
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        Icons.add,
                        color: isDark ? const Color(0xFFE3E3E3) : Colors.black87,
                        size: 20,
                      ),
                      const SizedBox(width: 8),
                      Text(
                        'Новый диалог',
                        style: TextStyle(
                          color: isDark ? const Color(0xFFE3E3E3) : Colors.black87,
                          fontWeight: FontWeight.w600,
                          fontSize: 14,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(height: 20),
          const Text(
            'История запросов',
            style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Colors.grey),
          ),
          const SizedBox(height: 10),
          // History Session List
          Expanded(
            child: provider.sessions.isEmpty
                ? const Center(
                    child: Text(
                      'Нет сессий',
                      style: TextStyle(color: Colors.grey, fontSize: 13),
                    ),
                  )
                : ListView.builder(
                    itemCount: provider.sessions.length,
                    itemBuilder: (context, index) {
                      final session = provider.sessions[index];
                      final isSelected = session.id == provider.activeSessionId;

                      return Container(
                        margin: const EdgeInsets.symmetric(vertical: 2, horizontal: 4),
                        decoration: BoxDecoration(
                          color: isSelected
                              ? (isDark ? const Color(0xFF232428) : Colors.black.withValues(alpha: 0.05))
                              : Colors.transparent,
                          borderRadius: BorderRadius.circular(16),
                          border: isSelected && isDark
                              ? Border.all(color: Colors.white.withValues(alpha: 0.05), width: 0.8)
                              : null,
                        ),
                        child: ListTile(
                          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
                          title: Text(
                            session.title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontSize: 13.5,
                              fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                              color: isSelected
                                  ? (isDark ? Colors.white : Theme.of(context).primaryColor)
                                  : (isDark ? Colors.grey.shade400 : Colors.grey.shade800),
                            ),
                          ),
                          leading: Icon(
                            Icons.chat_bubble_outline,
                            size: 18,
                            color: isSelected
                                ? (isDark ? Colors.white70 : Theme.of(context).primaryColor)
                                : Colors.grey,
                          ),
                          trailing: IconButton(
                            icon: const Icon(Icons.delete_outline, size: 18),
                            color: Colors.redAccent.withValues(alpha: 0.7),
                            onPressed: () {
                              provider.deleteSession(session.id);
                            },
                          ),
                          onTap: () {
                            provider.selectSession(session.id);
                            if (MediaQuery.of(context).size.width <= 800) {
                              Navigator.pop(context);
                            }
                          },
                        ),
                      );
                    },
                  ),
          ),
          Divider(color: isDark ? Colors.white.withValues(alpha: 0.05) : null),
          // Server connection URL info
          _buildServerInfo(context, provider),
          const SizedBox(height: 10),
          // Theme Toggle Button
          Material(
            color: Colors.transparent,
            child: ListTile(
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              leading: Icon(
                provider.isDarkMode ? Icons.wb_sunny : Icons.nights_stay,
                color: isDark ? Colors.white70 : Colors.black54,
              ),
              title: Text(
                provider.isDarkMode ? 'Светлая тема' : 'Темная тема',
                style: TextStyle(
                  color: isDark ? const Color(0xFFE3E3E3) : Colors.black87,
                  fontSize: 13.5,
                ),
              ),
              onTap: () {
                provider.toggleTheme();
              },
            ),
          ),
        ],
      ),
    );
  }

  // Server Info Section
  Widget _buildServerInfo(BuildContext context, ChatProvider provider) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF232428) : const Color(0xFFF1F5F9),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isDark ? Colors.white.withValues(alpha: 0.05) : Colors.black12,
          width: 0.8,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'Адрес сервера',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 11, color: Colors.grey),
              ),
              GestureDetector(
                onTap: () => _showEditUrlDialog(context, provider),
                child: Row(
                  children: [
                    Icon(
                      Icons.edit,
                      size: 12,
                      color: isDark ? const Color(0xFFE3E3E3) : Theme.of(context).primaryColor,
                    ),
                    const SizedBox(width: 4),
                    Text(
                      'Изм.',
                      style: TextStyle(
                        fontSize: 11,
                        color: isDark ? const Color(0xFFE3E3E3) : Theme.of(context).primaryColor,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            provider.baseUrl,
            style: TextStyle(
              fontSize: 12,
              fontFamily: 'monospace',
              color: isDark ? const Color(0xFF81C784) : Colors.green.shade700,
            ),
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }

  void _showEditUrlDialog(BuildContext context, ChatProvider provider) {
    final controller = TextEditingController(text: provider.baseUrl);
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('Настройка сервера StatIQ'),
          content: TextField(
            controller: controller,
            decoration: const InputDecoration(
              labelText: 'URL бэкенда',
              hintText: 'http://localhost:8090',
              border: OutlineInputBorder(),
            ),
          ),
          actions: [
            TextButton(
              child: const Text('Отмена'),
              onPressed: () => Navigator.pop(context),
            ),
            ElevatedButton(
              child: const Text('Сохранить'),
              onPressed: () {
                provider.updateBaseUrl(controller.text.trim());
                Navigator.pop(context);
              },
            ),
          ],
        );
      },
    );
  }

  // Chat Area
  Widget _buildChatArea(BuildContext context, ChatProvider provider, bool isWideScreen) {
    final messages = provider.activeMessages;

    return Column(
      children: [
        Expanded(
          child: messages.isEmpty
              ? _buildEmptyState(context, provider)
              : ListView.builder(
                  controller: _scrollController,
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 20),
                  itemCount: messages.length + (provider.isLoading ? 1 : 0),
                  itemBuilder: (context, index) {
                    if (index == messages.length && provider.isLoading) {
                      return _buildShimmerLoading(context);
                    }
                    return _buildMessageItem(context, messages[index]);
                  },
                ),
        ),
        if (provider.isLoading)
          const LinearProgressIndicator(
            minHeight: 2.5,
            backgroundColor: Colors.transparent,
          ),
        _buildInputArea(context, provider),
      ],
    );
  }

  // Empty State Widget
  Widget _buildEmptyState(BuildContext context, ChatProvider provider) {
    final isUser = provider.currentMode == 'user';
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Center(
      child: Container(
        constraints: const BoxConstraints(maxWidth: 500),
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              isUser ? Icons.assistant : Icons.analytics_outlined,
              size: 64,
              color: isDark ? const Color(0xFFE3E3E3).withValues(alpha: 0.5) : Theme.of(context).primaryColor.withValues(alpha: 0.5),
            ),
            const SizedBox(height: 16),
            Text(
              isUser
                  ? 'Добро пожаловать в StatIQ!'
                  : 'Режим Аналитика StatIQ',
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.bold,
                color: isDark ? const Color(0xFFE3E3E3) : Colors.black87,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              isUser
                  ? 'Задайте любой вопрос по нормативно-справочной информации, правилам расчетов, ROLES.md или общим данным Бюро национальной статистики.'
                  : 'В режиме аналитика система ориентирована на глубокий разбор регламентов, баз метаданных БДАП и сложных сопоставлений нормативно-справочной документации.',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 14,
                color: isDark ? Colors.white60 : Colors.grey.shade600,
                height: 1.45,
              ),
            ),
            const SizedBox(height: 24),
            // Quick suggestions
            Wrap(
              spacing: 8,
              runSpacing: 8,
              alignment: WrapAlignment.center,
              children: [
                _buildSuggestionCard(context, 'Где посмотреть ROLES.md?', provider),
                _buildSuggestionCard(context, 'Каковы стандарты БДАП?', provider),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSuggestionCard(BuildContext context, String text, ChatProvider provider) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return InkWell(
      borderRadius: BorderRadius.circular(24),
      onTap: () {
        _messageController.text = text;
        _handleSend(provider);
      },
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF232428) : Colors.black.withValues(alpha: 0.05),
          borderRadius: BorderRadius.circular(24),
          border: Border.all(
            color: isDark ? Colors.white.withValues(alpha: 0.05) : Colors.black12,
            width: 0.8,
          ),
        ),
        child: Text(
          text,
          style: TextStyle(
            fontSize: 13,
            color: isDark ? const Color(0xFFE3E3E3) : Colors.black87,
            fontWeight: FontWeight.w500,
          ),
        ),
      ),
    );
  }


  // Shimmer animation during loading
  Widget _buildShimmerLoading(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CircleAvatar(
            backgroundColor: Theme.of(context).primaryColor.withValues(alpha: 0.2),
            radius: 18,
            child: Icon(Icons.auto_awesome, size: 18, color: Theme.of(context).primaryColor),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Shimmer.fromColors(
              baseColor: Theme.of(context).brightness == Brightness.dark
                  ? const Color(0xFF1E293B)
                  : const Color(0xFFE2E8F0),
              highlightColor: Theme.of(context).brightness == Brightness.dark
                  ? const Color(0xFF334155)
                  : const Color(0xFFF1F5F9),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: double.infinity,
                    height: 14.0,
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(4.0),
                    ),
                  ),
                  const SizedBox(height: 8.0),
                  Container(
                    width: double.infinity,
                    height: 14.0,
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(4.0),
                    ),
                  ),
                  const SizedBox(height: 8.0),
                  Container(
                    width: 200.0,
                    height: 14.0,
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(4.0),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  // Render a single message
  Widget _buildMessageItem(BuildContext context, ChatMessage message) {
    final isUser = message.role == 'user';
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: isUser ? MainAxisAlignment.end : MainAxisAlignment.start,
        children: [
          if (!isUser) ...[
            CircleAvatar(
              backgroundColor: isDark ? Colors.white.withValues(alpha: 0.06) : Theme.of(context).primaryColor.withValues(alpha: 0.15),
              radius: 18,
              child: Icon(
                Icons.auto_awesome,
                size: 16,
                color: isDark ? const Color(0xFFE3E3E3) : Theme.of(context).primaryColor,
              ),
            ),
            const SizedBox(width: 12),
          ],
          Flexible(
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: BoxDecoration(
                color: isUser
                    ? (isDark ? const Color(0xFF282A2D) : Theme.of(context).primaryColor)
                    : (isDark ? Colors.transparent : Theme.of(context).cardColor),
                borderRadius: BorderRadius.only(
                  topLeft: const Radius.circular(16),
                  topRight: const Radius.circular(16),
                  bottomLeft: Radius.circular(isUser ? 16 : 4),
                  bottomRight: Radius.circular(isUser ? 4 : 16),
                ),
                border: !isUser && isDark
                    ? Border.all(color: Colors.white.withValues(alpha: 0.05), width: 0.8)
                    : null,
              ),
              child: isUser
                  ? Text(
                      message.content,
                      style: TextStyle(
                        color: isDark ? const Color(0xFFE3E3E3) : Colors.white,
                        fontSize: 14.5,
                      ),
                    )
                  : _buildAssistantMessage(context, message.content),
            ),
          ),
          if (isUser) ...[
            const SizedBox(width: 12),
            CircleAvatar(
              backgroundColor: isDark ? Colors.white.withValues(alpha: 0.05) : Theme.of(context).dividerColor,
              radius: 18,
              child: Icon(Icons.person, size: 16, color: isDark ? Colors.white60 : Colors.grey),
            ),
          ]
        ],
      ),
    );
  }

  // Helper to split message and render Warning Banner if starts with '⚠️ Ответ из общих знаний'
  Widget _buildAssistantMessage(BuildContext context, String content) {
    final isWarning = content.startsWith('⚠️ Ответ из общих знаний');
    final isDark = Theme.of(context).brightness == Brightness.dark;
    String mainMarkdown = content;
    Widget? warningBanner;

    if (isWarning) {
      final lines = content.split('\n');
      final firstLine = lines.first;
      
      warningBanner = Container(
        margin: const EdgeInsets.only(bottom: 12.0),
        padding: const EdgeInsets.symmetric(horizontal: 12.0, vertical: 10.0),
        decoration: BoxDecoration(
          color: Colors.amber.withValues(alpha: 0.05),
          border: Border.all(color: Colors.amber.withValues(alpha: 0.15), width: 0.8),
          borderRadius: BorderRadius.circular(12.0),
        ),
        child: Row(
          children: [
            Icon(Icons.warning_amber_rounded, color: Colors.amber.shade700, size: 20),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                firstLine,
                style: TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 13,
                  color: isDark ? Colors.amber.shade200 : Colors.amber.shade900,
                ),
              ),
            ),
          ],
        ),
      );
      mainMarkdown = lines.skip(1).join('\n').trim();
    }

    // НАЧАЛО ХАКА ПЕРЕХВАТА MERMAID
    // Превращаем блоки ```mermaid код ``` в MermaidRenderWidget
    String processedMarkdown = mainMarkdown;
    final regExp = RegExp(r'```mermaid\s*([\s\S]*?)\s*```');
    
    List<Widget> customMermaidWidgets = [];
    processedMarkdown = processedMarkdown.replaceAllMapped(regExp, (match) {
      final mermaidCode = match.group(1) ?? '';
      customMermaidWidgets.add(MermaidRenderWidget(code: mermaidCode.trim()));
      return '';
    });
    // КОНЕЦ ХАКА ПЕРЕХВАТА

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        if (warningBanner != null) warningBanner,
        MarkdownBody(
          data: processedMarkdown.trim(),
          selectable: true,
          shrinkWrap: true,
          styleSheet: MarkdownStyleSheet.fromTheme(Theme.of(context)).copyWith(
            p: const TextStyle(fontSize: 14.5, height: 1.45),
            code: TextStyle(
              backgroundColor: isDark ? const Color(0xFF282A2D) : const Color(0xFFE2E8F0),
              fontFamily: 'monospace',
              fontSize: 12.5,
              fontWeight: FontWeight.bold,
            ),
            codeblockDecoration: BoxDecoration(
              color: isDark ? const Color(0xFF131314) : const Color(0xFFF1F5F9),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: isDark ? Colors.white.withValues(alpha: 0.08) : const Color(0xFFE2E8F0),
              ),
            ),
            tableBorder: TableBorder.all(
              color: isDark ? Colors.white.withValues(alpha: 0.08) : Theme.of(context).dividerColor,
              width: 0.8,
            ),
            tableCellsPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            tableHead: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
            tableBody: const TextStyle(fontSize: 13),
          ),
        ),
        ...customMermaidWidgets,
      ],
    );}

  // Input Field Widget
  Widget _buildInputArea(BuildContext context, ChatProvider provider) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 16.0),
      decoration: BoxDecoration(
        color: Theme.of(context).scaffoldBackgroundColor,
        border: Border(
          top: BorderSide(
            color: isDark ? Colors.white.withValues(alpha: 0.05) : Theme.of(context).dividerColor,
            width: 0.5,
          ),
        ),
      ),
      child: Center(
        child: Container(
          constraints: const BoxConstraints(maxWidth: 800),
          decoration: BoxDecoration(
            color: isDark ? const Color(0xFF1C1B1F) : const Color(0xFFF1F5F9),
            borderRadius: BorderRadius.circular(32),
            border: Border.all(
              color: isDark ? Colors.white.withValues(alpha: 0.08) : Colors.black12,
              width: 0.8,
            ),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 6),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              const SizedBox(width: 12),
              Expanded(
                child: TextField(
                  controller: _messageController,
                  maxLines: 5,
                  minLines: 1,
                  textInputAction: TextInputAction.newline,
                  onSubmitted: (_) => _handleSend(provider),
                  style: TextStyle(
                    fontSize: 14.5,
                    color: isDark ? const Color(0xFFE3E3E3) : Colors.black87,
                  ),
                  decoration: const InputDecoration(
                    hintText: 'Спросите StatIQ...',
                    hintStyle: TextStyle(fontSize: 14, color: Colors.grey),
                    border: InputBorder.none,
                    focusedBorder: InputBorder.none,
                    enabledBorder: InputBorder.none,
                    errorBorder: InputBorder.none,
                    disabledBorder: InputBorder.none,
                    contentPadding: EdgeInsets.symmetric(vertical: 10),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              GestureDetector(
                onTap: provider.isLoading ? null : () => _handleSend(provider),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 200),
                  height: 40,
                  width: 40,
                  decoration: BoxDecoration(
                    color: provider.isLoading
                        ? Colors.transparent
                        : (isDark ? Colors.white.withValues(alpha: 0.08) : Theme.of(context).primaryColor),
                    shape: BoxShape.circle,
                  ),
                  child: provider.isLoading
                      ? const Padding(
                          padding: EdgeInsets.all(12.0),
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            valueColor: AlwaysStoppedAnimation<Color>(Colors.white70),
                          ),
                        )
                      : Icon(
                          Icons.arrow_upward,
                          color: isDark ? Colors.white70 : Colors.white,
                          size: 20,
                        ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _handleSend(ChatProvider provider) {
    final text = _messageController.text.trim();
    if (text.isNotEmpty) {
      _messageController.clear();
      provider.sendMessage(text);
      _scrollToBottom();
    }
  }
}
