package com.example.adhdlist.screens

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithTag
import io.github.kakaocup.compose.node.element.ComposeScreen
import io.github.kakaocup.compose.node.element.KNode
import io.github.kakaocup.compose.node.element.lazylist.KLazyListItemNode
import io.github.kakaocup.compose.node.element.lazylist.KLazyListNode

class TasksScreen(semanticsProvider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<TasksScreen>(
        semanticsProvider = semanticsProvider
    ) {
    val title: KNode = child {
        hasTestTag("Title")
    }
    val addTextField: KNode = child {
        hasTestTag("TasksTextField")
    }
    val addButton: KNode = child {
        hasTestTag("AddButton")
    }

    val list = KLazyListNode(
        semanticsProvider = semanticsProvider,
        viewBuilderAction = { hasTestTag("TasksLazyList") },
        itemTypeBuilder = { itemType(::LazyTaskItemNode) },
        positionMatcher = { position: Int ->

            SemanticsMatcher("") {
                try {
                    semanticsProvider.onNodeWithTag("TaskListElement$position").assertExists()
                    true
                } catch (e: AssertionError) {
                    false
                }
            }
        }
    )
}

class LazyTaskItemNode(
    semanticsNode: SemanticsNode,
    semanticsProvider: SemanticsNodeInteractionsProvider
) : KLazyListItemNode<LazyListOfListItemNode>(semanticsNode, semanticsProvider) {
    val text: KNode = child {
        hasTestTag("TaskText")
    }
    val checkbox: KNode = child {
        hasTestTag("TaskCheckBox")
    }
}