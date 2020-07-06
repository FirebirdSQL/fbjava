MODULES	:= fbjava

TARGET	:= release

CXX	:= g++
LD	:= $(CXX)

SRC_DIR		:= src/native
BUILD_DIR	:= build
OUT_DIR		:= output

ifeq ($(OS),Windows_NT)
SHRLIB_EXT	:= dll
else
SHRLIB_EXT	:= so
endif

OBJ_DIR := $(BUILD_DIR)/$(TARGET)
LIB_DIR := $(OUT_DIR)/$(TARGET)/lib

SRC_DIRS := $(addprefix $(SRC_DIR)/,$(MODULES))
OBJ_DIRS := $(addprefix $(OBJ_DIR)/,$(MODULES))

SRCS := $(foreach sdir,$(SRC_DIRS),$(wildcard $(sdir)/*.cpp))
OBJS := $(patsubst $(SRC_DIR)/%.cpp,$(OBJ_DIR)/%.o,$(SRCS))

CXX_FLAGS := -ggdb -MMD -MP -std=c++17 -Isrc/native/fbjava/include
LD_FLAGS  := -static-libgcc -static-libstdc++

ifneq ($(OS),Windows_NT)
CXX_FLAGS += -fPIC -fvisibility=hidden
endif

ifeq ($(TARGET),release)
	CXX_FLAGS += -O3
endif

vpath %.cpp $(SRC_DIRS)

define compile
$1/%.o: %.cpp
	$(CXX) -c $$(CXX_FLAGS) $$< -o $$@
endef

.PHONY: all mkdirs clean

all: mkdirs \
	$(LIB_DIR)/libfbjava.$(SHRLIB_EXT) \

mkdirs: $(OBJ_DIRS) $(LIB_DIR)

$(OBJ_DIRS) $(LIB_DIR):
	@"mkdir" -p $@

clean:
	@rm -rf $(BUILD_DIR) $(OUT_DIR)

$(foreach bdir,$(OBJ_DIRS),$(eval $(call compile,$(bdir))))

-include $(addsuffix .d,$(basename $(OBJS)))

$(LIB_DIR)/libfbjava.$(SHRLIB_EXT): $(OBJ_DIR)/fbjava/fbjava.o
	$(LD) -shared $(LD_FLAGS) $^ -o $@
