package com.example.adhdlist.presentation.view.lists.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adhdlist.data.model.TaskList
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Preview
@Composable
private fun TaskListElementPreview() {
    TaskListListElement(index = 0, taskList = TaskList("Zadania na dzis"), onItemSwiped = {}, onClicked = {})
}

@Composable
fun TaskListListElement(
    index: Int,
    taskList: TaskList,
    onItemSwiped: () -> Unit,
    onClicked: () -> Unit,
) {
    var normalBoxHeight by remember { mutableIntStateOf(0) }
    var leftIconVisibility by remember { mutableStateOf(false) }
    var rightIconVisibility by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val offset = remember {
        Animatable(0f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .testTag("TaskListListElement$index")
    ) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.error)
                .height(with(LocalDensity.current) { normalBoxHeight.toDp() })
                .fillMaxWidth()
        ) { // Delete Box
            when {
                leftIconVisibility -> {
                    Row(
                        modifier = Modifier
                            .height(with(LocalDensity.current) { normalBoxHeight.toDp() })
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            modifier = Modifier.padding(8.dp, 2.dp),
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete Icon",
                            tint = MaterialTheme.colorScheme.onError
                        )
                        Box(modifier = Modifier)
                    }
                }

                rightIconVisibility -> {
                    Row(
                        modifier = Modifier
                            .height(with(LocalDensity.current) { normalBoxHeight.toDp() })
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier)
                        Icon(
                            modifier = Modifier.padding(8.dp, 2.dp),
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete Icon",
                            tint = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .height(IntrinsicSize.Max)
                .fillMaxWidth()
                .offset {
                    IntOffset(offset.value.roundToInt(), 0)
                }
                .clickable {
                    onClicked()
                }
                .draggable(
                    state = rememberDraggableState { delta ->
                        coroutineScope.launch {
                            offset.snapTo(offset.value + delta)
                        }
                    },
                    orientation = Orientation.Horizontal,
                    onDragStarted = {
                        coroutineScope.launch {
                            when {

                                offset.value > 0 -> {
                                    leftIconVisibility = true
                                    rightIconVisibility = false

                                }

                                offset.value < 0 -> {
                                    rightIconVisibility = true
                                    leftIconVisibility = false
                                }
                            }
                        }
                    },
                    onDragStopped = {
                        coroutineScope.launch {
                            when {
                                offset.value < 500 && offset.value > -500 -> {
                                    offset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(
                                            durationMillis = 500,
                                            delayMillis = 0
                                        )
                                    )

                                    rightIconVisibility = false
                                    leftIconVisibility = false
                                }

                                offset.value >= 500 -> {
                                    offset.animateTo(
                                        targetValue = 1200f,
                                        animationSpec = tween(
                                            durationMillis = 500,
                                            delayMillis = 0
                                        )
                                    )
                                    onItemSwiped()
                                }

                                offset.value <= -500 -> {
                                    offset.animateTo(
                                        targetValue = -1200f,
                                        animationSpec = tween(
                                            durationMillis = 500,
                                            delayMillis = 0
                                        )
                                    )
                                    onItemSwiped()
                                }
                            }
                        }
                    }
                )
                .onGloballyPositioned { coordinates ->
                    normalBoxHeight = coordinates.size.height
                }
        ) {
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .height(IntrinsicSize.Max)
                    .padding(8.dp, 20.dp)
                    .fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        taskList.name,
                        modifier = Modifier
                            .height(IntrinsicSize.Max)
                            .fillMaxWidth()
                            .padding(2.dp),
                        fontSize = 22.sp,
                    )
                }
            }
        }
    }
}
