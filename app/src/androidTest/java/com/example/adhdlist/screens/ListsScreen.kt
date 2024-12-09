package com.example.adhdlist.screens

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithTag
import io.github.kakaocup.compose.node.element.ComposeScreen
import io.github.kakaocup.compose.node.element.KNode
import io.github.kakaocup.compose.node.element.lazylist.KLazyListItemNode
import io.github.kakaocup.compose.node.element.lazylist.KLazyListNode

class ListsScreen(semanticsProvider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<ListsScreen>(
    semanticsProvider = semanticsProvider
) {


    val addTextField: KNode = child {
        hasTestTag("ListsTextField")
    }
    val addButton: KNode = child {
        hasTestTag("AddButton")
    }

    val list = KLazyListNode(
        semanticsProvider = semanticsProvider,
        viewBuilderAction = { hasTestTag("ListLazyList") },
        itemTypeBuilder = { itemType(::LazyListOfListItemNode) },
        positionMatcher = { position: Int ->

            SemanticsMatcher("") {
                try {
                    semanticsProvider.onNodeWithTag("TaskListListElement$position").assertExists()
                    true
                } catch (e: AssertionError) {
                    false
                }
            }
        }
    )
}

class LazyListOfListItemNode(
    semanticsNode: SemanticsNode,
    semanticsProvider: SemanticsNodeInteractionsProvider

) : KLazyListItemNode<LazyListOfListItemNode>(semanticsNode, semanticsProvider)